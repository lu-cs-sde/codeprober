package codeprober.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
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
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import codeprober.protocol.ClientRequest;
import codeprober.protocol.data.AsyncRpcUpdate;
import codeprober.protocol.data.BackingFile;
import codeprober.protocol.data.InitInfo;
import codeprober.protocol.data.protocolgen_spec_InitInfo_1;
import codeprober.util.ParsedArgs;
import codeprober.util.SessionLogger;
import codeprober.util.VersionInfo;

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
//			System.out.println("num out bytes: " + fullStrData.length);
			if (fullStrData.length == 0) {
				System.err.println("Writing empty message to client??");
				dst.write(new byte[] { (byte) 129, (byte) 0 });
				dst.flush();
				return;
			}

			final int chunkSize = 32000;
			final int neededChunks = (fullStrData.length / chunkSize) + (fullStrData.length % chunkSize == 0 ? 0 : 1);
			for (int chunk = 0; chunk < neededChunks; chunk++) {
				final byte[] strData = Arrays.copyOfRange(fullStrData, chunk * chunkSize,
						Math.min(fullStrData.length, (chunk + 1) * chunkSize));

				final int lenPart;
				if (strData.length <= 125) {
					lenPart = 1;
				} else if (strData.length < Short.MAX_VALUE) {
					lenPart = 3;
				} else {
					lenPart = 9;
				}

				final byte[] padded = new byte[1 + lenPart + strData.length];

				// Header bits:
				// 0: Continuation frame
				// 1: Text frame
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
					padded[6] = (byte) ((strData.length >>> 24) & 0xFF);
					padded[7] = (byte) ((strData.length >>> 16) & 0xFF);
					padded[8] = (byte) ((strData.length >>> 8) & 0xFF);
					padded[9] = (byte) (strData.length & 0xFF);
				}
				System.arraycopy(strData, 0, padded, 1 + lenPart, strData.length);

				dst.write(padded);
				dst.flush();
			}
		}
	}

	static InitInfo getInitMsg(ParsedArgs args) {
		final VersionInfo vinfo = VersionInfo.getInstance();
		final Integer buildTimeSeconds = vinfo.buildTimeSeconds;
		final int bufferTime = CodespacesCompat.getChangeBufferTime();
		final boolean disableVersionCheckerByDefault = "true"
				.equals(System.getenv("DISABLE_VERISON_CHECKER_BY_DEFAULT"));

		final File backingFile = BackingFileSettings.getRealFileToBeUsedInRequests();
		String backingFileContents = null;
		if (backingFile != null) {
			if (!backingFile.exists()) {
				System.out.println("Backing file does not exist yet. Proceeding with empty initial state");
				backingFileContents = "";
			} else {
				backingFileContents = BackingFileSettings.readBackingFileContents();
				if (backingFileContents == null) {
					backingFileContents = "";
				}
			}
		}

		return new InitInfo( //
				new protocolgen_spec_InitInfo_1(vinfo.revision, vinfo.clean, buildTimeSeconds), //
				bufferTime > 0 ? bufferTime : null, //
				args.workerProcessCount, //
				disableVersionCheckerByDefault ? true : null, //
				backingFile == null ? null : new BackingFile(backingFile.getPath(), backingFileContents) //
		);
	}

	static void handleRequest(Socket socket, ParsedArgs args, ServerToClientMessagePusher msgPusher,
			Function<ClientRequest, JSONObject> onQuery, AtomicBoolean connectionIsAlive, SessionLogger logger)
			throws IOException, NoSuchAlgorithmException {
		final InputStream in = socket.getInputStream();
		@SuppressWarnings("resource")
		final Scanner s = new Scanner(in, "UTF-8");
		final String data = s.useDelimiter("\\r\\n\\r\\n").next();
		if (WebServer.handleAuthCookieRelatedRequest(data, socket.getInetAddress(), socket.getOutputStream())) {
			return;
		}
		handleRequestWithPreparsedData(socket, args, msgPusher, onQuery, connectionIsAlive, data, logger);
	}

	static void handleRequestWithPreparsedData(Socket socket, ParsedArgs args, ServerToClientMessagePusher msgPusher,
			Function<ClientRequest, JSONObject> onQuery, AtomicBoolean connectionIsAlive, String data,
			SessionLogger logger) throws IOException, NoSuchAlgorithmException {
		final InputStream in = socket.getInputStream();
		final OutputStream out = socket.getOutputStream();

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

				final WorkspacePathFilteringUpdateListener wsPathFilter = new WorkspacePathFilteringUpdateListener();
				final Consumer<ServerToClientEvent> changeListener = wsPathFilter.getFilteringChangeListener(event -> {
					final JSONObject message = event.getUpdateMessage();
					if (message == null) {
						return;
					}
					try {
						// No synchronized() needed, it is done in the write method
						writeWsMessage(out, message.toString());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});
				msgPusher.addChangeListener(changeListener);
				final Runnable cleanup = () -> msgPusher.removeChangeListener(changeListener);

				if (logger != null) {
					logger.log(new JSONObject() //
							.put("t", "ClientConnected") //
							.put("p", "ws"));
				}
				writeWsMessage(out, getInitMsg(args).toJSON().toString());

				final Consumer<AsyncRpcUpdate> asyncMessageWriter = asyncMsg -> {
					try {
						writeWsMessage(out, asyncMsg.toJSON().toString());
					} catch (IOException e) {
						System.err.println("Failed sending async message");
						e.printStackTrace();
						try {
							socket.close();
						} catch (IOException e1) {
							System.out.println("Failed to close the message after failed message");
							e1.printStackTrace();
						}
					}
				};
				while (true) {

					int first = in.read();
					if ((first & 8) == 8) {
						// Close frame, don't worry about the contents, just close
						cleanup.run();
						return;
					}
					ByteArrayOutputStream frameBuffer = null;
					readFrame: while (true) {
						boolean isFin = (first & 128) == 128;
						switch (first & 0x7F) {
						case 0: // Continuation
							if (frameBuffer == null) {
								System.err.println("Got continuation frame without an initial text frame");
								System.exit(1);
							}
							// Else, fall down to 'text frame'
						case 1: { // Text
//							System.out.println("got text frame, fin: " + isFin +" ; ");

							// -128, strip away the 'mask' bit
							final int lenIndicator = in.read() - 128;
							final byte[] reqData;

							if (lenIndicator >= 0 && lenIndicator <= 125) {
								reqData = new byte[lenIndicator];
							} else if (lenIndicator == 126) {
								reqData = new byte[(in.read() << 8) | in.read()];
							} else {
								reqData = new byte[(int) ((((long) in.read()) << 56) | (((long) in.read()) << 48)
										| ((long) in.read() << 40) | ((long) in.read() << 32) | (in.read() << 24)
										| (in.read() << 16) | (in.read() << 8) | in.read())];
							}
							final byte[] key = new byte[4];
							readFully(in, key);
							readFully(in, reqData);

							// Decode request data
							for (int i = 0; i < reqData.length; i++) {
								reqData[i] = (byte) (reqData[i] ^ key[i & 0x3]);
							}

							if (isFin && frameBuffer == null) {
								// Only a single frame
								final JSONObject jobj = new JSONObject(new String(reqData, StandardCharsets.UTF_8));

								writeWsMessage(out, onQuery.apply(
										new ClientRequest(jobj, asyncMessageWriter, connectionIsAlive, wsPathFilter))
										.toString());
								break readFrame;
							} else {
								if (frameBuffer == null) {
									frameBuffer = new ByteArrayOutputStream();
								}
								frameBuffer.write(reqData);
								if (isFin) {
									final JSONObject jobj = new JSONObject(
											new String(frameBuffer.toByteArray(), StandardCharsets.UTF_8));
									writeWsMessage(out, onQuery.apply(new ClientRequest(jobj, asyncMessageWriter,
											connectionIsAlive, wsPathFilter)).toString());
									break readFrame;
								} else {
									first = in.read();
								}
							}
//						System.out.println(Arrays.toString(reqData));
//						System.out.println("json: " + jobj.toString(2));

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
		}
		System.out.println("Not a get request.. ? From " + socket.getRemoteSocketAddress() + " :: " + data);
	}

	public static boolean shouldAcceptRemoteConnections() {
		return "true".equals(System.getenv("PERMIT_REMOTE_CONNECTIONS"));
	}

	public static boolean shouldDelegateWebsocketToHttp() {
		final String portOverride = System.getenv("WEBSOCKET_SERVER_PORT");
		return portOverride != null && portOverride.toLowerCase(Locale.ENGLISH).equals("http");
	}

	/**
	 * If the port is set to 0, then the system will automatically assign a port.
	 * livePort is set to whichever port was given to us. If null, then a websocket
	 * server hasn't been started yet.
	 */
	private static Integer livePort;

	public static int getPort() {
		if (livePort != null) {
			return livePort;
		}
		final String portOverride = System.getenv("WEBSOCKET_SERVER_PORT");
		if (portOverride != null) {
			try {
				return Integer.parseInt(portOverride);
			} catch (NumberFormatException e) {
				System.out.println("Invalid websocket port override '" + portOverride + "', ignoring");
				e.printStackTrace();
			}
		}
		return WebServer.getFallbackPort();
	}

	public static void start(ParsedArgs args, ServerToClientMessagePusher msgPusher,
			Function<ClientRequest, JSONObject> onQuery, Runnable onSomeClientDisconnected, SessionLogger logger) {
		final int port = getPort();
		try (ServerSocket server = new ServerSocket(port, 0, null)) {
			livePort = server.getLocalPort();
			System.out.println("Started WebSocket server on port " + livePort);
			while (true) {
				Socket s = server.accept();
				System.out.println("New WS connection from " + s.getRemoteSocketAddress());
				new Thread(() -> {
					final AtomicBoolean connectionIsAlive = new AtomicBoolean(true);
					try {
						handleRequest(s, args, msgPusher, onQuery, connectionIsAlive, logger);
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
						connectionIsAlive.set(false);
						onSomeClientDisconnected.run();
					}
				}).start();
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
