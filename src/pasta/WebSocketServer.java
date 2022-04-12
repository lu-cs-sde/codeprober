package pasta;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

public class WebSocketServer {

	private static void readFully(InputStream src, byte[] dst) throws IOException {
		int readPos = 0;
		int read;
		while ((read = src.read(dst, readPos, dst.length - readPos)) != -1) {
			readPos += read;
			if (readPos >= dst.length) {
				return;
			}
		}
	}

	private static void writeWsMessage(OutputStream dst, String msg) throws IOException {
		synchronized (dst) {
			final byte[] fullStrData = msg.getBytes(StandardCharsets.UTF_8);
			if (fullStrData.length == 0) {
				System.err.println("Writing empty message to client??");
				dst.write(new byte[] {
						(byte)129,
						(byte)0
				});
				dst.flush();
				return;
			}
			
			final int chunkSize = 32000;
			final int neededChunks = (fullStrData.length / chunkSize) + (fullStrData.length % chunkSize == 0 ? 0 : 1);
			for (int chunk = 0; chunk < neededChunks; chunk++) {
				final byte[] strData = Arrays.copyOfRange(fullStrData, chunk * chunkSize, Math.min(fullStrData.length, (chunk + 1) * chunkSize));
				
				final int lenPart;
				if (strData.length <= 125) {
					lenPart = 1;
				} else if (strData.length < Short.MAX_VALUE) {
					lenPart = 3;
				} else {
					lenPart = 9;
//				throw new Error("TODO write really long msgs, len: " + strData.length);
				}
				
				final byte[] padded = new byte[1 + lenPart + strData.length];
				
				// Header bits:
				//   0: Continuation frame
				//   1: Text frame
				// 128: Last frame
				if (chunk == neededChunks - 1) {
					// Last frame
					if (neededChunks == 1) {
						padded[0] = (byte) (1 | 128);
					} else {
						padded[0] = (byte) 128;
					}
				} else if (chunk == 0) {
					// First of multiple frames
					padded[0] = (byte) 1;
				} else {
					// Middle (non-first, non-last) frame
					padded[0] = (byte) 0;
				}
				if (strData.length <= 125) {
					padded[1] = (byte) (strData.length);
				} else if (strData.length < Short.MAX_VALUE) {
					padded[1] = (byte) (126);
					padded[2] = (byte) ((strData.length >> 8) & 0xFF);
					padded[3] = (byte) (strData.length & 0xFF);
				} else {
					padded[1] = (byte) (127);
					// High bits -> 0
					for (int i = 2; i < 6; i++) {
						padded[i] = 0;
					}
					// Low bits -> string len
					padded[6] = (byte)((strData.length >>> 24) & 0xFF);
					padded[7] = (byte)((strData.length >>> 16) & 0xFF);
					padded[8] = (byte)((strData.length >>> 8) & 0xFF);
					padded[9] = (byte)(strData.length & 0xFF);
				}
				System.arraycopy(strData, 0, padded, 1 + lenPart, strData.length);
				System.out.println("Chunk " + chunk +", msglen " + strData.length + " , lenPart " + lenPart);
				System.out.println("Writing " + Arrays.toString(Arrays.copyOfRange(padded, 0, Math.min(padded.length, 16))) +"..");
				dst.write(padded);
				dst.flush();
//				try {
//					Thread.sleep(1000L);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
			}
		}
	}

	private static void writeWsMessageOld(OutputStream dst, String msg) throws IOException {
		synchronized (dst) {
			final byte[] strData = msg.getBytes(StandardCharsets.UTF_8);
			final int lenPart;
			if (strData.length <= 125) {
				lenPart = 1;
			} else if (strData.length < Short.MAX_VALUE) {
				lenPart = 3;
			} else {
				lenPart = 9;
//				throw new Error("TODO write really long msgs, len: " + strData.length);
			}

			final byte[] padded = new byte[1 + lenPart + strData.length];
			padded[0] = (byte) 129;
			if (strData.length <= 125) {
				padded[1] = (byte) (strData.length);
			} else if (strData.length < Short.MAX_VALUE) {
				padded[1] = (byte) (126);
				padded[2] = (byte) ((strData.length >> 8) & 0xFF);
				padded[3] = (byte) (strData.length & 0xFF);
			} else {
				padded[1] = (byte) (127);
				// High bits -> 0
				for (int i = 2; i < 6; i++) {
					padded[i] = 0;
				}
				// Low bits -> string len
				padded[6] = (byte)((strData.length >>> 24) & 0xFF);
				padded[7] = (byte)((strData.length >>> 16) & 0xFF);
				padded[8] = (byte)((strData.length >>> 8) & 0xFF);
				padded[9] = (byte)(strData.length & 0xFF);
			}
			System.arraycopy(strData, 0, padded, 1 + lenPart, strData.length);
			System.out.println("msglen " + strData.length + " , lenPart " + lenPart);
			System.out.println("Writing " + Arrays.toString(Arrays.copyOfRange(padded, 0, Math.min(padded.length, 16))) +"..");
			dst.write(padded);
			dst.flush();
		}
	}

	private static void handleRequest(Socket socket, List<Runnable> onJarChangeListeners,
			Function<JSONObject, String> onQuery) throws IOException, NoSuchAlgorithmException {
		InputStream in = socket.getInputStream();
		OutputStream out = socket.getOutputStream();
		@SuppressWarnings("resource")
		Scanner s = new Scanner(in, "UTF-8");
		String data = s.useDelimiter("\\r\\n\\r\\n").next();
		System.out.println("data: " + data);
		System.out.println("---");
		Matcher get = Pattern.compile("^GET").matcher(data);
		if (get.find()) {
			Matcher webSocketReq = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
			if (webSocketReq.find()) {
				byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n" + "Connection: Upgrade\r\n"
						+ "Upgrade: websocket\r\n" + "Sec-WebSocket-Accept: "
						+ Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(
								(webSocketReq.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")))
						+ "\r\n\r\n").getBytes("UTF-8");
				out.write(response, 0, response.length);
				out.flush();

//				byte[] decoded = new byte[6];
//	          byte[] encoded = new byte[] { (byte) 198, (byte) 131, (byte) 130, (byte) 182, (byte) 194, (byte) 135 };
//	          byte[] key = new byte[] { (byte) 167, (byte) 225, (byte) 225, (byte) 210 };
//	          for (int i = 0; i < encoded.length; i++) {
//	            decoded[i] = (byte) (encoded[i] ^ key[i & 0x3]);
//	          }
//	          System.out.println(Arrays.toString(decoded));

				final Runnable onJarChange = () -> {
					try {
						// No synchronized() needed, it is done in the write method
						writeWsMessage(out, "{\"type\":\"refresh\"}");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				};
				onJarChangeListeners.add(onJarChange);
				Runnable cleanup = () -> onJarChangeListeners.remove(onJarChange);

//				136
				writeWsMessage(out, "{\"type\":\"init-pasta\"}");
//				writeWsMessage(out, "{\"hello\": \"world\"}");
//				writeWsMessage(out, "{\"hello\": \"world\"}");
				while (true) {
					System.out.println("Waiting for more data..");
					final int first = in.read();
					if ((first &  8) == 8) {
						// Close frame, don't worry about the contents, just close
						cleanup.run();
						return;
					}
					switch (first) {
					case 129: {
						System.out.println("Got expected first byte");

						// -128, strip away the 'mask' big
						final int lenIndicator = in.read() - 128;
						final byte[] reqData;

						if (lenIndicator >= 0 && lenIndicator <= 125) {
							reqData = new byte[lenIndicator];
						} else if (lenIndicator == 126) {
							reqData = new byte[(in.read() << 8) | in.read()];
						} else {
							reqData = new byte[(in.read() << 56) | (in.read() << 48) | (in.read() << 40) | (in.read() << 32) | (in.read() << 24) | (in.read() << 16) | (in.read() << 8) | in.read()];
//							throw new Error("TODO handle big requests " + lenIndicator);
						}
						System.out.println("Got req w/ len " + reqData.length + ", lenid: " + lenIndicator);
						
						final byte[] key = new byte[4];
						readFully(in, key);
						System.out.println("Reading data..");
						readFully(in, reqData);

						System.out.println(Arrays.toString(key));
//						System.out.println(Arrays.toString(reqData));

						// Decode request data
						for (int i = 0; i < reqData.length; i++) {
							reqData[i] = (byte) (reqData[i] ^ key[i & 0x3]);
						}
//						System.out.println(Arrays.toString(reqData));
						final JSONObject jobj = new JSONObject(new String(reqData, StandardCharsets.UTF_8));
//						System.out.println("json: " + jobj.toString(2));

						writeWsMessage(out, onQuery.apply(jobj));

						break;
					}
//					case 136: {
//						// Connection close
//						System.out.println("Client disconnected");
//
//						cleanup.run();
//						return;
//					}
					default: {
						System.out.println("Got unexpected first byte: " + first);
						for (int i = 0; i < 111; i++) {
							System.out.println("next " + i + " :: " + in.read());
						}
						System.exit(1);
					}
					}
//					System.out.println(in.read());
				}
			}
		}
		System.out.println("Not a get request.. ?" + data);
	}

	static void start(List<Runnable> onJarChangeListeners, Function<JSONObject, String> onQuery) {
		final int port = 8080;
		try (ServerSocket server = new ServerSocket(port)) {
			System.out.println("Started WebSocket server on port " + port);
			while (true) {
				Socket s = server.accept();
				new Thread(() -> {
					try {
						handleRequest(s, onJarChangeListeners, onQuery);
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
