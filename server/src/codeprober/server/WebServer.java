package codeprober.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import codeprober.CodeProber;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.data.LongPollResponse;
import codeprober.protocol.data.RequestAdapter;
import codeprober.protocol.data.TopRequestReq;
import codeprober.protocol.data.TopRequestRes;
import codeprober.protocol.data.TopRequestResponseData;
import codeprober.protocol.data.TunneledWsPutRequestReq;
import codeprober.protocol.data.TunneledWsPutRequestRes;
import codeprober.protocol.data.WsPutInitReq;
import codeprober.protocol.data.WsPutInitRes;
import codeprober.protocol.data.WsPutLongpollReq;
import codeprober.protocol.data.WsPutLongpollRes;
import codeprober.util.ParsedArgs;

public class WebServer {
	private static class WsPutSession {
		public final String id;
		public long lastActivity = System.currentTimeMillis();
		private final ConcurrentLinkedQueue<JSONObject> outgoingMessages = new ConcurrentLinkedQueue<>();
		public final AtomicInteger activeConnections = new AtomicInteger();
		public final AtomicBoolean isConnected = new AtomicBoolean(true);

		private final AtomicInteger lastEventVersion = new AtomicInteger(0);

		public WsPutSession(String id) {
			this.id = id;
		}

		public synchronized void onServerEvent(int eventVersion) {
			if (lastEventVersion.getAndSet(eventVersion) == eventVersion) {
				return;
			}
			notifyAll();
		}

		public synchronized void addMessage(JSONObject obj) {
			outgoingMessages.add(obj);
			notifyAll();
		}

		public synchronized LongPollResponse pollForThreeMinutes(int clientKnownEventVersion)
				throws InterruptedException {
			final LongPollResponse immediate = pollWithoutWaiting(clientKnownEventVersion);
			if (immediate != null) {
				return immediate;
			}
			// 3 minutes
			wait(3 * 60 * 1000);
			return pollWithoutWaiting(clientKnownEventVersion);
		}

		private LongPollResponse pollWithoutWaiting(int clientKnownEventVersion) {
			final JSONObject msg = outgoingMessages.poll();
			if (msg != null) {
				return LongPollResponse.fromPush(msg);
			}
			if (lastEventVersion.get() != clientKnownEventVersion) {
				return LongPollResponse.fromEtag(lastEventVersion.get());
			}
			return null;
		}

		@Override
		public int hashCode() {
			return id.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof WsPutSession && id.equals(((WsPutSession) obj).id);
		}
	}

	private static class WsPutSessionMonitor {
		private final List<WsPutSession> sessions = new ArrayList<>();

		public synchronized void onServerEvent(int eventVersion) {
			for (WsPutSession wps : sessions) {
				wps.onServerEvent(eventVersion);
			}
		}

		public synchronized WsPutSession getOrCreate(String id) {
			for (WsPutSession wps : sessions) {
				if (wps.id.equals(id)) {
					wps.lastActivity = System.currentTimeMillis();
					return wps;
				}
			}
			final WsPutSession newSession = new WsPutSession(id);
			sessions.add(newSession);
			notifyAll();
			return newSession;
		}

		public synchronized boolean pollDisconnects() throws InterruptedException {
			if (sessions.isEmpty()) {
				wait();
			}
			if (sessions.isEmpty()) {
				return false;
			}
			long oldestActivity = sessions.get(0).lastActivity;
			for (int i = 1; i < sessions.size(); i++) {
				oldestActivity = Math.min(oldestActivity, sessions.get(i).lastActivity);
			}
			final int allowedIdleTimeMs = 30_000;
			// 30 seconds after last request - consider the client disconnected
			final long disconnectTime = oldestActivity + allowedIdleTimeMs;
			while (System.currentTimeMillis() < disconnectTime) {
				wait(Math.max(0L, disconnectTime - System.currentTimeMillis()));
			}
			final long cutoff = System.currentTimeMillis();
			final Iterator<WsPutSession> iter = sessions.iterator();
			boolean removedAny = false;
			boolean keptAnyDueToActiveConections = false;
			while (iter.hasNext()) {
				final WsPutSession wps = iter.next();
				if (cutoff >= (wps.lastActivity + allowedIdleTimeMs)) {
					if (wps.activeConnections.get() == 0) {
						iter.remove();
						wps.isConnected.set(false);
						removedAny = true;
					} else {
						keptAnyDueToActiveConections = true;
					}
				}
			}
			if (!removedAny && keptAnyDueToActiveConections) {
				// Sleep a few seconds to avoid this thread being active 100%
				wait(5_000);
			}
			return removedAny;
		}

	}

	private static String guessMimeType(String path) {
		switch (path.substring(path.lastIndexOf('.') + 1)) {
		case "html":
			return "text/html";
		case "js":
			return "text/javascript";
		case "css":
			return "text/css";
		case "svg":
			return "image/svg+xml";
		case "png":
			return "image/png";
		case "ico":
			return "image/x-icon";
		case "ttf":
			return "font/ttf";

		default:
			System.out.println("Don't know mime type of path " + path);
			return null;
		}
	}

	private static final String srcDirectoryOverride = System.getenv("WEB_RESOURCES_OVERRIDE");
	static {
		if (srcDirectoryOverride != null) {
			System.out.println("Using web override dir: " + srcDirectoryOverride);
		}
	}

	private final WsPutSessionMonitor monitor = new WsPutSessionMonitor();;

	private void handleGetRequest(Socket socket, String data) throws IOException {
		final OutputStream out = socket.getOutputStream();
		final Matcher getReqPathMatcher = Pattern.compile("^GET (.*) HTTP").matcher(data);
		if (!getReqPathMatcher.find()) {
			System.err.println("Missing path for GET request");
			return;
		}

		String path = getReqPathMatcher.group(1).split("[?#]")[0];
		if (path.endsWith("/")) {
			path += "index.html";
		}

		System.out.println("get " + path);
		if (path.equals("/WS_PORT")) {
			// Special magical resource, don't actually read from classPath/file system
			out.write("HTTP/1.1 200 OK\r\n".getBytes("UTF-8"));
			out.write(("Content-Type: text/plain\r\n").getBytes("UTF-8"));
			out.write(("\r\n").getBytes("UTF-8"));

			if (WebSocketServer.shouldDelegateWebsocketToHttp()) {
				out.write("http".getBytes("UTF-8"));
			} else if (CodespacesCompat.shouldApplyCompatHacks()) {
				out.write(("codespaces-compat:" + getPort() + ":" + WebSocketServer.getPort()).getBytes("UTF-8"));
			} else {
				out.write(("" + WebSocketServer.getPort()).getBytes("UTF-8"));
			}
			out.flush();
			return;
		}

//				Integer sizeHint = null;
		final InputStream stream;
		if (srcDirectoryOverride != null) {
			final File f = new File(srcDirectoryOverride, path);
			if (f.exists()) {
				stream = new FileInputStream(f);
//						sizeHint = (int)f.length();
			} else {
				stream = null;
			}
		} else {
			if (path.startsWith("/")) {
				path = path.substring(1);
			}
			stream = CodeProber.class.getResourceAsStream("resources/" + path);
		}
		if (stream == null) {
			System.out.println("Found no resource for path '" + path + "' in req " + data);
			out.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes("UTF-8"));
			return;
		}
		out.write("HTTP/1.1 200 OK\r\n".getBytes("UTF-8"));

		String mimeType = guessMimeType(path);
		if (mimeType != null) {
			out.write(("Content-Type: " + mimeType + "\r\n").getBytes("UTF-8"));
		}
		// if (sizeHint != null) {
		// out.write(("Content-Length: " + sizeHint + "\r\n").getBytes("UTF-8"));
		// }

		out.write("\r\n".getBytes("UTF-8"));

		final byte[] buf = new byte[512];
		int read;
		while ((read = stream.read(buf)) != -1) {
			out.write(buf, 0, read);
		}
		out.flush();
		stream.close();
	}

	private void handlePutRequest(Socket socket, String data, ParsedArgs args, ServerToClientMessagePusher msgPusher,
			Function<ClientRequest, JSONObject> onQuery) throws IOException {
		final String[] parts = data.split("\r\n");
		String putPath = null;
		int contentLen = -1;
		for (String s : parts) {
			if (s.startsWith("PUT ")) {
				putPath = s.split(" ")[1];
			} else if (s.startsWith("Content-Length: ")) {
				try {
					contentLen = Integer.parseInt(s.substring("Content-Length: ".length()));
				} catch (NumberFormatException e) {
					throw new IOException("Bad content-length value in " + s);
				}
			}
		}
		if (putPath == null || contentLen == -1) {
			throw new IOException("Bad put request");
		}

		final int fContentLen = contentLen;
		Supplier<byte[]> getBody = () -> {
			try {
				InputStream in = socket.getInputStream();
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				final byte[] buf = new byte[512];
				int read;
				while ((read = in.read(buf)) != -1) {
					baos.write(buf, 0, read);
					// System.out.println("read " + read + " bytes, now " + baos.size() + " / " +
					// fContentLen);
					if (baos.size() == fContentLen) {
						break;
					}
				}
				return baos.toByteArray();
			} catch (IOException e) {
				throw new RuntimeException("Failed reading PUT body", e);
			}

		};

		switch (putPath) {
		case "/wsput": {
			final JSONObject body = new JSONObject(new String(getBody.get(), StandardCharsets.UTF_8));
			final OutputStream out = socket.getOutputStream();

			final JSONObject responseObj;
			try {
				responseObj = new RequestAdapter() {

					@Override
					protected TopRequestRes handleTopRequest(TopRequestReq req) {
						final JSONObject resp = this.handle(req.data);
						return new TopRequestRes(req.id, TopRequestResponseData.fromSuccess(resp));
					}

					@Override
					protected WsPutInitRes handleWsPutInit(WsPutInitReq req) {
						final WsPutSession wps = monitor.getOrCreate(req.session);
						wps.activeConnections.incrementAndGet();
						try {
							return new WsPutInitRes(WebSocketServer.getInitMsg(args));
						} finally {
							wps.activeConnections.decrementAndGet();
						}
					}

					@Override
					protected WsPutLongpollRes handleWsPutLongpoll(WsPutLongpollReq req) {
						final WsPutSession wps = monitor.getOrCreate(req.session);
						wps.activeConnections.incrementAndGet();
						try {
							final LongPollResponse resp = wps.pollForThreeMinutes(req.etag);
							wps.lastActivity = System.currentTimeMillis(); // Mark as active at the end of a poll
							return new WsPutLongpollRes(resp);
						} catch (InterruptedException e) {
							System.err.println("/wsput request interrupted");
							throw new JSONException("Interrupted");
						} finally {
							wps.activeConnections.decrementAndGet();
						}
					}

					@Override
					protected TunneledWsPutRequestRes handleTunneledWsPutRequest(TunneledWsPutRequestReq req) {
						final WsPutSession wps = monitor.getOrCreate(req.session);
						final ClientRequest cr = new ClientRequest(req.request,
								asyncMsg -> wps.addMessage(asyncMsg.toJSON()), wps.isConnected);

						wps.activeConnections.incrementAndGet();
						try {
							final JSONObject resp = onQuery.apply(cr);
							return new TunneledWsPutRequestRes(resp);
						} finally {
							wps.activeConnections.decrementAndGet();
						}
					}

				}.handle(body);

			} catch (JSONException e) {
				System.out.println("Malformed wsput request");
				e.printStackTrace(System.out);
//				System.out.println("Bad request: " + body);
				out.write("HTTP/1.1 400 Bad Request\r\n".getBytes("UTF-8"));
				out.write(("\r\n").getBytes("UTF-8"));
				return;
			}

			if (responseObj == null) {
				System.out.println("Bad request: " + body);
				out.write("HTTP/1.1 400 Bad Request\r\n".getBytes("UTF-8"));
//				out.write(("Content-Type: text/plain\r\n").getBytes("UTF-8"));
//				out.write(("Content-Length: " + response.length + "\r\n").getBytes("UTF-8"));
				out.write(("\r\n").getBytes("UTF-8"));
				return;
			}

			final byte[] response = responseObj.toString().getBytes(StandardCharsets.UTF_8);
			out.write("HTTP/1.1 200 OK\r\n".getBytes("UTF-8"));
			out.write(("Content-Type: text/plain\r\n").getBytes("UTF-8"));
			out.write(("Content-Length: " + response.length + "\r\n").getBytes("UTF-8"));
			out.write(("\r\n").getBytes("UTF-8"));
			out.write(response);

//			final String remoteAddr = body.optString("session",
//					socket.getRemoteSocketAddress().toString().split(":")[0]);
//			final WsPutSession wps = monitor.getOrCreate(remoteAddr);
//			wps.activeConnections.incrementAndGet();
//			try {
//				switch (body.getString("type")) {
//				case "init": {
//					response = WebSocketServer.getInitMsg(args).toString().getBytes(StandardCharsets.UTF_8);
//					break;
//				}
//				case "longpoll": {
//					final int etag = body.getInt("etag");
//
//					final JSONObject message = wps.pollForThreeMinutes(etag);
//					wps.lastActivity = System.currentTimeMillis(); // Mark as active at the end of a poll
//
//					if (message == null) {
//						response = new JSONObject() //
//								.put("etag", etag).toString() //
//								.getBytes(StandardCharsets.UTF_8);
//					} else {
//						response = message.toString().getBytes(StandardCharsets.UTF_8);
//					}
//					break;
//				}
//				default: {
//					response = onQuery.apply(
//							new ClientRequest(body, asyncMsg -> wps.addMessage(asyncMsg.toJSON()), wps.isConnected))
//							.toString().getBytes(StandardCharsets.UTF_8);
//					break;
//				}
//				}
//			} catch (InterruptedException e) {
//				System.err.println("/wsput request interrupted");
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} finally {
//				wps.activeConnections.decrementAndGet();
//			}
			break;
		}
		default: {
			throw new IOException("Unsupported PUT path " + putPath);
		}
		}
	}

	private void handleRequest(Socket socket, ParsedArgs args, ServerToClientMessagePusher msgPusher,
			Function<ClientRequest, JSONObject> onQuery) throws IOException, NoSuchAlgorithmException {
		System.out.println("Incoming HTTP request from: " + socket.getRemoteSocketAddress());

		final InputStream in = socket.getInputStream();
		final ByteArrayOutputStream headerBaos = new ByteArrayOutputStream();

		// Reading 1 byte at a time is quite bad for efficiency, but as long as we only
		// do it for the header then it is not _too_ painful
		// We don't want to ready any more bytes than absolutely necessary, so that when
		// it comes time to read the HTTP body, the remaining bytes are available in the
		// stream.
		int read;
		int divisorState = 0;
		readHeader: while ((read = in.read()) != -1) {
			switch (read) {
			case '\r': {
				if (divisorState == 0 || divisorState == 2) {
					++divisorState;
					continue;
				}
				break;
			}
			case '\n': {
				if (divisorState == 1) {
					++divisorState;
					continue;
				}
				if (divisorState == 3) {
					break readHeader;
				}
				break;
			}
			}

			switch (divisorState) {
			case 1:
				headerBaos.write('\r');
				break;
			case 2:
				headerBaos.write('\r');
				headerBaos.write('\n');
				break;
			case 3:
				headerBaos.write('\r');
				headerBaos.write('\n');
				headerBaos.write('\r');
				break;

			default:
				break;
			}
			divisorState = 0;
			headerBaos.write(read);
		}

		final String headers = new String(headerBaos.toByteArray(), StandardCharsets.UTF_8);
		final Matcher get = Pattern.compile("^GET").matcher(headers);
		if (get.find()) {
			handleGetRequest(socket, headers);
			return;
		}
		final Matcher put = Pattern.compile("^PUT").matcher(headers);
		if (put.find()) {
			handlePutRequest(socket, headers, args, msgPusher, onQuery);
			return;
		}
		System.out.println("Not sure how to handle request " + headers);
	}

	static int getPort() {
		final String portOverride = System.getenv("WEB_SERVER_PORT");
		if (portOverride != null) {
			try {
				return Integer.parseInt(portOverride);
			} catch (NumberFormatException e) {
				System.out.println("Invalid web port override '" + portOverride + "', ignoring");
				e.printStackTrace();
			}
		}
		return 8000;
	}

	public static void start(ParsedArgs args, ServerToClientMessagePusher msgPusher,
			Function<ClientRequest, JSONObject> onQuery, Runnable onSomeClientDisconnected) {
		final int port = getPort();
		try (ServerSocket server = new ServerSocket(port, 0, WebSocketServer.createServerFilter())) {
			System.out.println(
					"Started web server on port " + port + ", visit http://localhost:" + port + "/ in your browser");
			final WebServer ws = new WebServer();
			new Thread(() -> {
				while (true) {
					try {
						if (ws.monitor.pollDisconnects()) {
							onSomeClientDisconnected.run();
						}
					} catch (InterruptedException e) {
						System.err.println("wsput disconnect thread interrupted");
						e.printStackTrace();
					} catch (RuntimeException e) {
						System.err.println("Unexpected error in disconnect poller");
						e.printStackTrace();
					}
				}
			}).start();
			msgPusher.addJarChangeListener(() -> {
				ws.monitor.onServerEvent(msgPusher.getEventCounter());
			});
			try {

				while (true) {
					Socket s = server.accept();
					new Thread(() -> {
						try {
							ws.handleRequest(s, args, msgPusher, onQuery);
						} catch (IOException | NoSuchAlgorithmException e) {
							System.out.println("Error while handling request");
							e.printStackTrace();
						} finally {
							try {
								s.close();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						System.out.println("Req handled for thread " + Thread.currentThread());
					}).start();
				}
			} catch (Throwable t) {
				t.printStackTrace();
				throw t;
			}
		} catch (IOException e) {
			e.printStackTrace();
      if (e.getMessage().contains("Address already in use")) {
				System.out.println(
						"You can run parallell CodeProber instances, but each instance must have unique WEB_SERVER_PORT and WEBSOCKET_SERVER_PORT");
				System.exit(1);
			}
			throw new RuntimeException(e);
		}
	}
}
