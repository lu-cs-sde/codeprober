package codeprober.server;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
import codeprober.rpc.JsonRequestHandler;
import codeprober.server.WorkspaceApi.Responder;
import codeprober.util.ParsedArgs;
import codeprober.util.SessionLogger;

public class WebServer {
	private static class WsPutSession {
		public final String id;
		public long lastActivity = System.currentTimeMillis();
		private final ConcurrentLinkedQueue<ServerToClientEvent> outgoingMessages = new ConcurrentLinkedQueue<>();
		public final AtomicInteger activeConnections = new AtomicInteger();
		public final AtomicBoolean isConnected = new AtomicBoolean(true);
		public final WorkspacePathFilteringUpdateListener updateListener = new WorkspacePathFilteringUpdateListener();

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

		public synchronized void addMessage(ServerToClientEvent obj) {
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
			final ServerToClientEvent event = outgoingMessages.poll();
			if (event != null) {
				if (updateListener.canIgnore(event)) {
					return pollWithoutWaiting(clientKnownEventVersion);
				}
				final JSONObject update = event.getUpdateMessage();
				if (update != null) {
					return LongPollResponse.fromPush(update);
				}
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

		public void broadcastMessage(ServerToClientEvent json) {
			System.out.println("Broadcasting " + json);
			for (WsPutSession wps : sessions) {
				wps.addMessage(json);
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

	public static final boolean DEBUG_REQUESTS = "true".equals(System.getenv("DEBUG_REQUESTS"));

	private final WsPutSessionMonitor monitor = new WsPutSessionMonitor();

	private void handleGetRequest(Socket socket, String data, ParsedArgs parsedArgs,
			ServerToClientMessagePusher msgPusher, Function<ClientRequest, JSONObject> onQuery,
			Runnable onSomeClientDisconnected, AtomicBoolean needsTool, SessionLogger logger) throws IOException {
		final OutputStream out = socket.getOutputStream();

		if (data.contains("\r\nUpgrade: websocket\r\n") || data.endsWith("\r\nUpgrade: websocket")) {
			final AtomicBoolean connectionIsAlive = new AtomicBoolean(true);
			try {
				try {
					WebSocketServer.handleRequestWithPreparsedData(socket, parsedArgs, msgPusher,
							JsonRequestHandler.createTopRequestHandler(onQuery), connectionIsAlive, data, logger);
				} catch (NoSuchAlgorithmException | IOException e) {
					System.out.println("Failed handling HTTP->WS upgrade request");
					System.out.printf(
							"Try running with the environment variable 'WEBSOCKET_SERVER_PORT=http' or 'WEBSOCKET_SERVER_PORT=%d' (or another port) if this problem persists\n",
							WebServer.getPort() + 1);
					e.printStackTrace();
				}
			} finally {
				try {
					socket.close();
				} catch (IOException e) {
					System.err.print("Error closing socket");
					e.printStackTrace();
				}
				connectionIsAlive.set(false);
				onSomeClientDisconnected.run();
			}
			return;
		}
		final Matcher getReqPathMatcher = Pattern.compile("^GET (.*) HTTP").matcher(data);
		if (!getReqPathMatcher.find()) {
			System.err.println("Missing path for GET request");
			return;
		}

		final String[] pathAndQueries = getReqPathMatcher.group(1).split("[?&#]");
		String path = pathAndQueries[0];
		if (path.endsWith("/")) {
			path += "index.html";
		}
		if (path.equals("/index.html") && needsTool.get()) {
			// Mask the request to upload.html. Will look like index.html in the browser,
			// but that is OK
			path = "/upload.html";
		}

		if (DEBUG_REQUESTS) {
			System.out.println("[get] addr=" + socket.getRemoteSocketAddress() + " | path= " + path);
		}
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
		if (path.equals("/LATEST_VERSION")) {
			// Also a special magical resource, don't actually read from classPath/file
			// system
			// This URL should be kept in sync with client/src/model/repositoryUrl.ts
			final URL url = new URL("https://api.github.com/repos/lu-cs-sde/codeprober/releases/latest");
			final HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			con.setConnectTimeout(1000);
			con.setReadTimeout(1000);

			final int status = con.getResponseCode();
			if (status != 200) {
				throw new RuntimeException("Unexpected status code " + status);
			}
			final BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			final StringBuffer content = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				content.append(inputLine + "\n");
			}
			con.disconnect();

			String tagName;
			final String fullVersionFile = content.toString();
			try {
				final JSONObject parsed = new JSONObject(fullVersionFile);
				tagName = parsed.getString("tag_name");
			} catch (JSONException e) {
				System.err.println("Unxpected response from releases/latest");
				e.printStackTrace();
				out.write("HTTP/1.1 502 Bad Gateway\r\n".getBytes("UTF-8"));
				out.write("\r\n".getBytes("UTF-8"));
				return;
			}
			final byte[] respBytes = tagName.getBytes("UTF-8");

			write200(out, respBytes);
			return;
		}
		if (path.equals("/api/workspace")) {
			final WorkspaceApi api = new WorkspaceApi(Responder.fromOutputStream(socket.getOutputStream()));
			api.getWorkspace();
			return;
		}
		if (path.equals("/api/workspace/contents")) {
			final WorkspaceApi api = new WorkspaceApi(Responder.fromOutputStream(socket.getOutputStream()));
			final String wsPath = findAndDecodeFilePathQueryParam(pathAndQueries, "f=");
			api.getContents(wsPath);
			return;
		}

		final InputStream stream = openSourceFileStream(path);
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

		out.write("\r\n".getBytes("UTF-8"));

		final byte[] buf = new byte[512];
		int read;
		while ((read = stream.read(buf)) != -1) {
			out.write(buf, 0, read);
		}
		out.flush();
		stream.close();
	}

	private InputStream openSourceFileStream(String path) throws IOException {
		if (srcDirectoryOverride != null) {
			final File f = new File(srcDirectoryOverride, path);
			if (f.exists()) {
				return new FileInputStream(f);
			} else {
				return null;
			}
		} else {
			if (path.startsWith("/")) {
				path = path.substring(1);
			}
			return CodeProber.class.getResourceAsStream("resources/" + path);
		}
	}

	private static String decodeURIComponent(String comp) {
		// https://stackoverflow.com/a/6926987
		try {
			return URLDecoder.decode(comp.replace("+", "%2B"), StandardCharsets.UTF_8.name()).replace("%2B", "+");
		} catch (UnsupportedEncodingException impossible) {
			System.out.println("UTF-8 not supported??");
			impossible.printStackTrace();
			return comp;
		}
	}

	private void handlePutRequest(Socket socket, String data, ParsedArgs args, ServerToClientMessagePusher msgPusher,
			Function<ClientRequest, JSONObject> onQuery, Consumer<String> setUnderlyingJarPath, SessionLogger logger)
			throws IOException {
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
		if (DEBUG_REQUESTS) {
			System.out.println("[put] addr=" + socket.getRemoteSocketAddress() + " | path= " + putPath);
		}

		final String[] pathAndQueries = putPath.split("[?&#]");
		putPath = pathAndQueries[0];

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
						if (logger != null) {
							logger.log(new JSONObject() //
									.put("t", "ClientConnected") //
									.put("p", "http"));
						}
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
								asyncMsg -> wps.addMessage(ServerToClientEvent.rawMessage(asyncMsg.toJSON())),
								wps.isConnected, wps.updateListener::onWorkspacePathChanged);

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
				write400(out);
				return;
			}

			if (responseObj == null) {
				System.out.println("Bad request: " + body);
				write400(out);
				return;
			}

			final byte[] response = responseObj.toString().getBytes(StandardCharsets.UTF_8);
			write200(out, response);
			break;
		}
		case "/upload": {
			final OutputStream out = socket.getOutputStream();

			final byte[] toolData = getBody.get();
			final File tmp = File.createTempFile("cpr_", "_tool.jar");
			Files.write(tmp.toPath(), toolData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			tmp.deleteOnExit();
			setUnderlyingJarPath.accept(tmp.getAbsolutePath());
			write200(out);
			break;
		}

		case "/api/workspace/contents": {
			final WorkspaceApi api = new WorkspaceApi(Responder.fromOutputStream(socket.getOutputStream()));
			final String wsPath = findAndDecodeFilePathQueryParam(pathAndQueries, "f=");
			api.putContents(wsPath, fContentLen, socket::getInputStream);
			System.out.println("done..?");
			return;
		}
		case "/api/workspace/metadata": {
			final WorkspaceApi api = new WorkspaceApi(Responder.fromOutputStream(socket.getOutputStream()));
			final String wsPath = findAndDecodeFilePathQueryParam(pathAndQueries, "f=");
			api.putMetadata(wsPath, fContentLen, socket::getInputStream);
			return;
		}
		case "/api/workspace/rename": {
			final WorkspaceApi api = new WorkspaceApi(Responder.fromOutputStream(socket.getOutputStream()));
			System.out.println("Searching in " + Arrays.toString(pathAndQueries));
			final String srcPath = findAndDecodeFilePathQueryParam(pathAndQueries, "src=");
			final String dstPath = findAndDecodeFilePathQueryParam(pathAndQueries, "dst=");
			api.rename(srcPath, dstPath);
			return;
		}
		case "/api/workspace/unlink": {
			final WorkspaceApi api = new WorkspaceApi(Responder.fromOutputStream(socket.getOutputStream()));
			api.unlink(findAndDecodeFilePathQueryParam(pathAndQueries, "f="));
			return;
		}
		default: {
			throw new IOException("Unsupported PUT path " + putPath);
		}
		}
	}

	static void write400(OutputStream out) throws IOException {
		out.write("HTTP/1.1 400 Bad Request\r\n".getBytes("UTF-8"));
		out.write(("\r\n").getBytes("UTF-8"));
		out.flush();
	}

	static void write200(OutputStream out) throws IOException {
		out.write("HTTP/1.1 200 OK\r\n".getBytes("UTF-8"));
		out.write(("\r\n").getBytes("UTF-8"));
		out.flush();
	}

	static void write200(OutputStream out, byte[] data) throws IOException {
		out.write("HTTP/1.1 200 OK\r\n".getBytes("UTF-8"));
		out.write(("Content-Type: text/plain\r\n").getBytes("UTF-8"));
		out.write(("Content-Length: " + data.length + "\r\n").getBytes("UTF-8"));
		out.write(("\r\n").getBytes("UTF-8"));
		out.write(data);
		out.flush();
	}

	private static String findAndDecodeFilePathQueryParam(String[] pathAndQueries, String needle) {
		String val = findAndDecodeQueryParam(pathAndQueries, needle);
		if (val == null) {
			return null;
		}
		if (File.separatorChar != '/') {
			// Windows
			return val.replace('/', File.separatorChar);
		}
		return val;
	}

	private static String findAndDecodeQueryParam(String[] pathAndQueries, String needle) {
		for (int i = 1 /* Start at 1 to skip path */; i < pathAndQueries.length; ++i) {
			final String ent = pathAndQueries[i];
			if (ent.startsWith(needle)) {
				return decodeURIComponent(ent.substring(needle.length()));
			}
		}
		return null;
	}

	private void handleRequest(Socket socket, ParsedArgs args, ServerToClientMessagePusher msgPusher,
			Function<ClientRequest, JSONObject> onQuery, Runnable onSomeClientDisconnected, AtomicBoolean needsTool,
			Consumer<String> setUnderlyingJarPath, SessionLogger logger) throws IOException, NoSuchAlgorithmException {

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
		if (handleAuthCookieRelatedRequest(headers, socket.getInetAddress(), socket.getOutputStream())) {
			return;
		}

		final Matcher get = Pattern.compile("^GET").matcher(headers);
		if (get.find()) {
			handleGetRequest(socket, headers, args, msgPusher, onQuery, onSomeClientDisconnected, needsTool, logger);
			return;
		}
		final Matcher put = Pattern.compile("^PUT").matcher(headers);
		if (put.find()) {
			handlePutRequest(socket, headers, args, msgPusher, onQuery, setUnderlyingJarPath, logger);
			return;
		}
		System.out.println("Not sure how to handle request " + headers);
	}

	static boolean handleAuthCookieRelatedRequest(String headers, InetAddress incomingAddress, OutputStream out)
			throws UnsupportedEncodingException, IOException {
		final String[] newAuthUrlAndToken = extractNewAuthToken(headers);
		if (newAuthUrlAndToken != null) {
			out.write("HTTP/1.1 303 See Other\r\n".getBytes("UTF-8"));
			out.write(String
					.format("Set-Cookie: cpr-auth=%s; HttpOnly; Max-Age=%d\r\n", newAuthUrlAndToken[1], 3600 * 24 * 365)
					.getBytes("UTF-8"));
			out.write(String.format("Location: %s\r\n", newAuthUrlAndToken[0]).getBytes("UTF-8"));
			out.write(("\r\n").getBytes("UTF-8"));
			return true;
		}
		if (incomingAddress.isLoopbackAddress()) {
			// Always accept localhost connections
			return false;
		}
		if (WebSocketServer.shouldAcceptRemoteConnections()) {
			// User specifically configured remote connections to be acceptable
			return false;
		}
		final String authCookie = extractAuthCookie(headers);
		if (authCookie == null) {
			out.write("HTTP/1.1 403 Forbidden\r\n".getBytes("UTF-8"));
			out.write("Content-Type: text/plain\r\n".getBytes("UTF-8"));
			out.write(("\r\n").getBytes("UTF-8"));
			out.write(
					"Missing auth header. Use the url displayed where you start CodeProber. Should be in the format 'http://localhost:8000?auth=auth-key-here'\n\nTo disable authentication, start CodeProber with PERMIT_REMOTE_CONNECTIONS set to true."
							.getBytes("UTF-8"));
			return true;
		}
		if (!authCookie.equals(WebServer.validAuthKey)) {
			out.write("HTTP/1.1 403 Forbidden\r\n".getBytes("UTF-8"));
			out.write("Content-Type: text/plain\r\n".getBytes("UTF-8"));
			out.write(("\r\n").getBytes("UTF-8"));
			out.write(
					"Invalid auth header. Use the url displayed where you start CodeProber. Should be in the format 'http://localhost:8000?auth=auth-key-here'\n\nTo disable authentication, start CodeProber with PERMIT_REMOTE_CONNECTIONS set to true."
							.getBytes("UTF-8"));
			return true;
		}
		return false;

	}

	private static String[] extractNewAuthToken(String headers) {
		final Matcher getReqPathMatcher = Pattern.compile("^GET (.*) HTTP").matcher(headers);
		if (!getReqPathMatcher.find()) {
			return null;
		}

		final String[] pathAndSearch = getReqPathMatcher.group(1).split("[?]");
		if (pathAndSearch.length == 1) {
			return null;
		}

		final String search = pathAndSearch[1];
		final String[] searchParts = search.split("&");
		for (String part : searchParts) {
			if (part.startsWith("auth=")) {
				final String authVal = part.substring("auth=".length());
				if (Pattern.compile("[a-zA-Z0-9-_+]+").matcher(authVal).matches()) {
					final String newSearch;
					if (search.contains(part + "&")) {
						newSearch = "?" + search.replace(part + "&", "");
					} else if (search.contains("&" + part)) {
						newSearch = "?" + search.replace("&" + part, "");
					} else {
						newSearch = "";
					}

					return new String[] { pathAndSearch[0] + newSearch, authVal };
				} else {
					System.err.println("Got invalid authVal '" + authVal + "'");
					return null;
				}
			}
		}
		return null;
	}

	private static String extractAuthCookie(String headers) {
		final String cookieNeedle = "\r\nCookie:";
		final int cookieIdx = headers.indexOf(cookieNeedle);
		if (cookieIdx == -1) {
			return null;
		}
		int cookieEnd = headers.indexOf("\r\n", cookieIdx + cookieNeedle.length());
		if (cookieEnd == -1) {
			// Assume the cookie string goes all the way to the end of the headers
			cookieEnd = headers.length();
		}
		final String cookieData = headers.substring(cookieIdx + cookieNeedle.length(), cookieEnd);
		for (String part : cookieData.split(";")) {
			part = part.trim();
			if (part.startsWith("cpr-auth=")) {
				final String authVal = part.substring("cpr-auth=".length());
				return authVal;
			}
		}
		return null;
	}

	private static Integer actualPort = null;

	public static int getPort() {
		if (actualPort != null) {
			return actualPort;
		}
		final String specificPortOverride = System.getenv("WEB_SERVER_PORT");
		if (specificPortOverride != null) {
			try {
				return Integer.parseInt(specificPortOverride);
			} catch (NumberFormatException e) {
				System.out.println("Invalid web port override '" + specificPortOverride + "', ignoring");
				e.printStackTrace();
			}
		}
		return getFallbackPort();
	}

	public static int getFallbackPort() {
		final String portOverride = System.getenv("PORT");
		if (portOverride != null) {
			try {
				final int parsed = Integer.parseInt(portOverride);
				return (parsed == 0 && actualPort != null) ? actualPort : parsed;
			} catch (NumberFormatException e) {
				System.out.println("Invalid web port override '" + portOverride + "', ignoring");
				e.printStackTrace();
			}
		}
		return 8000;
	}

	private static String validAuthKey;
	static {
		final SecureRandom secureRandom = new SecureRandom();
		final byte[] randomBytes = new byte[16];
		secureRandom.nextBytes(randomBytes);
		String keyTail = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(randomBytes);
		if (keyTail.length() > 12) {
			keyTail = keyTail.substring(0, 12);
		}
		validAuthKey = "key-" + keyTail;

	}

	public static void start(ParsedArgs args, ServerToClientMessagePusher msgPusher,
			Function<ClientRequest, JSONObject> onQuery, Runnable onSomeClientDisconnected, AtomicBoolean needsTool,
			Consumer<String> setUnderlyingJarPath, SessionLogger logger) {

		final int port = getPort();
		try (ServerSocket server = new ServerSocket(port, 0, null)) {
			// if port=0, then the port number is automatically allocated.
			// Use 'actualPort' to get the port that was really opened, as 0 is invalid.
			actualPort = server.getLocalPort();
			if ("true".equals(System.getenv("GRADLEPLUGIN"))) {
				// stdout is programatically parsed by the plugin, print the port in a format it
				// expects.
				System.out.printf("CPRGRADLE_URL=http://localhost:%d?auth=%s%n", actualPort, validAuthKey);
			}
			System.out.printf("Started web server on port %d, visit 'http://localhost:%d?auth=%s' in your browser%n",
					actualPort, actualPort, validAuthKey);
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
			msgPusher.addChangeListener((event) -> {
				switch (event.type) {
				case JAR_CHANGED: {
					ws.monitor.onServerEvent(msgPusher.getEventCounter());
					break;
				}
				default: {
					ws.monitor.broadcastMessage(event);
					break;
				}
				}
			});
			try {

				while (true) {
					Socket s = server.accept();
					new Thread(() -> {
						try {
							ws.handleRequest(s, args, msgPusher, onQuery, onSomeClientDisconnected, needsTool,
									setUnderlyingJarPath, logger);
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
//						System.out.println("Req handled for thread " + Thread.currentThread());
					}).start();
				}
			} catch (Throwable t) {
				t.printStackTrace();
				throw t;
			}
		} catch (IOException e) {
			e.printStackTrace();
			if (e.getMessage().contains("Address already in use")) {
				System.out
						.println("You can run parallell CodeProber instances, but each instance must have unique PORT");
				System.exit(1);
			}
			throw new RuntimeException(e);
		}
	}
}
