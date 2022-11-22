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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import codeprober.CodeProber;

public class WebServer {
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

	private static void handleGetRequest(Socket socket, String data) throws IOException {
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

	private static void handlePutRequest(Socket socket, String data, ServerToClientMessagePusher msgPusher,
			Function<JSONObject, String> onQuery) throws IOException {
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
			final byte[] response;
			final JSONObject body = new JSONObject(new String(getBody.get(), StandardCharsets.UTF_8));
			switch (body.getString("type")) {
			case "init": {
				response = WebSocketServer.getInitMsg().toString().getBytes(StandardCharsets.UTF_8);
				break;
			}
			case "longpoll": {
				final int etag = body.getInt("etag");
				final int newEtag = msgPusher.pollEvent(etag);
				response = new JSONObject() //
						.put("etag", newEtag).toString() //
						.getBytes(StandardCharsets.UTF_8);
				break;
			}
			default: {
				response = onQuery.apply(body).getBytes(StandardCharsets.UTF_8);
				break;
			}
			}
			final OutputStream out = socket.getOutputStream();
			out.write("HTTP/1.1 200 OK\r\n".getBytes("UTF-8"));
			out.write(("Content-Type: text/plain\r\n").getBytes("UTF-8"));
			out.write(("Content-Length: " + response + "\r\n").getBytes("UTF-8"));
			out.write(("\r\n").getBytes("UTF-8"));
			out.write(response);
			break;
		}
		default: {
			throw new IOException("Unsupported PUT path " + putPath);
		}
		}
	}

	private static void handleRequest(Socket socket, ServerToClientMessagePusher msgPusher,
			Function<JSONObject, String> onQuery) throws IOException, NoSuchAlgorithmException {
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
			handlePutRequest(socket, headers, msgPusher, onQuery);
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

	public static void start(ServerToClientMessagePusher msgPusher, Function<JSONObject, String> onQuery) {
		final int port = getPort();
		try (ServerSocket server = new ServerSocket(port, 0, WebSocketServer.createServerFilter())) {
			System.out.println(
					"Started web server on port " + port + ", visit http://localhost:" + port + "/ in your browser");
			while (true) {
				Socket s = server.accept();
				new Thread(() -> {
					try {
						handleRequest(s, msgPusher, onQuery);
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
				}).start();
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
