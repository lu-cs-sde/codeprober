package pasta;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

		default:
			System.out.println("Don't know mime type of path " + path);
			return null;
		}
	}
	
	private static final String srcDirectoryOverride = System.getenv("PASTA_WEB_OVERRIDE"); 
	static {
		if (srcDirectoryOverride != null) {
			System.out.println("Using web override dir: " + srcDirectoryOverride);
		}
	}

	private static void handleRequest(Socket socket)
			throws IOException, NoSuchAlgorithmException {
		System.out.println("Incoming HTTP request from: " + socket.getRemoteSocketAddress());
//		socket.getRemoteSocketAddress()
		InputStream in = socket.getInputStream();
		OutputStream out = socket.getOutputStream();
		@SuppressWarnings("resource")
		Scanner s = new Scanner(in, "UTF-8");
		String data = s.useDelimiter("\\r\\n\\r\\n").next();
//		System.out.println("data: " + data);
//		System.out.println("---");
		Matcher get = Pattern.compile("^GET").matcher(data);
		if (get.find()) {
			Matcher normalGetReq = Pattern.compile("^GET (.*) HTTP").matcher(data);
			if (normalGetReq.find()) {

				String path = normalGetReq.group(1).split("[?#]")[0];
				if (path.endsWith("/")) {
					path += "index.html";
				}

				final InputStream stream;
				if (srcDirectoryOverride != null) {
					final File f = new File(srcDirectoryOverride, path);
					if (f.exists()) {
						stream = new FileInputStream(f);
					} else {
						stream = null;
					}
				} else {
					if (path.startsWith("/")) {
						path = path.substring(1);
					}
					stream = WebServer.class.getResourceAsStream("resources/" + path);
				}
				if (stream == null) {
					System.out.println("Found no resource for path '" + path +"' in req " + data);
					out.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes("UTF-8"));
					return;
				}
				out.write("HTTP/1.1 200 OK\r\n".getBytes("UTF-8"));

				String mimeType = guessMimeType(path);
				if (mimeType != null) {
					out.write(("Content-Type: " + mimeType + "\r\n").getBytes("UTF-8"));
				}

				out.write("\r\n\r\n".getBytes("UTF-8"));

				final byte[] buf = new byte[512];
				int read;
				while ((read = stream.read(buf)) != -1) {
					out.write(buf, 0, read);
				}
				out.flush();
				stream.close();

				return;
			}
		}
		System.out.println("Not sure how to handle request " + data);
	}

	static void start() {
		final int port = 8000;
		try (ServerSocket server = new ServerSocket(port, 0, InetAddress.getByName(null))) {
			System.out.println("Started web server on port " + port + ", visit http://localhost:8000/ in your browser");
			while (true) {
				Socket s = server.accept();
				new Thread(() -> {
					try {
						handleRequest(s);
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
