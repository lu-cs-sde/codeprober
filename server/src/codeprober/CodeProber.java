package codeprober;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import org.json.JSONException;
import org.json.JSONObject;

import codeprober.RunAllTests.MergedResult;
import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.protocol.ClientRequest;
import codeprober.rpc.JsonRequestHandler;
import codeprober.server.CodespacesCompat;
import codeprober.server.ServerToClientMessagePusher;
import codeprober.server.WebServer;
import codeprober.server.WebSocketServer;
import codeprober.toolglue.UnderlyingTool;
import codeprober.util.FileMonitor;
import codeprober.util.ParsedArgs;
import codeprober.util.ParsedArgs.ConcurrencyMode;
import codeprober.util.VersionInfo;

public class CodeProber {

	public static void printUsage() {
		System.out.println(
				"Usage: java -jar code-prober.jar [--test] path/to/your/analyzer-or-compiler.jar [args-to-forward-to-your-main]");
	}

	static void flog(String msg) {
		final String flogPath = System.getenv("FLOG_PATH");
		if (flogPath != null) {

			try {
				Files.write(new File(flogPath).toPath(), (msg + "\n").getBytes(StandardCharsets.UTF_8),
						StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] mainArgs) throws IOException {
		System.out.println("Starting server, version: " + VersionInfo.getInstance().toString() + "..");
		if (mainArgs.length == 0) {
			printUsage();
			System.exit(1);
		}

		final ParsedArgs parsedArgs = ParsedArgs.parse(mainArgs);

		final JsonRequestHandler defaultHandler = new DefaultRequestHandler(UnderlyingTool.fromJar(parsedArgs.jarPath), parsedArgs.extraArgs);
		final JsonRequestHandler userFacingHandler;
		flog(Arrays.toString(mainArgs));

		switch (parsedArgs.concurrencyMode) {

		case COORDINATOR: {
			try {
				userFacingHandler = new ConcurrentCoordinator(defaultHandler, parsedArgs.jarPath, parsedArgs.extraArgs,
						parsedArgs.workerProcessCount);
			} catch (IOException e) {
				System.err.println(
						"Error while initializing concurrent mode. Are you running CodeProber from a jar file?");
				throw e;
			}
			break;
		}

		case WORKER: {
			final PrintStream realOut = System.out;
			StdIoInterceptor io = new StdIoInterceptor() {

				@Override
				public void onLine(boolean stdout, String line) {
					flog(line);
				}
			};
			io.install();
			final Consumer<JSONObject> writeToCoordinator = msgObj -> {
//				System.out.println("WriteToCoordinator " + msgObj);
				final byte[] data = msgObj.toString().getBytes(StandardCharsets.UTF_8);
				synchronized (realOut) {
					realOut.println();
					realOut.print("<" + data.length + ">");
					try {
						realOut.write(data);
					} catch (IOException e) {
						// 'should never happen'
						flog("Error when writing to standard out");
						e.printStackTrace();
						throw new RuntimeException(e);
					}
					realOut.println();
				}

			};
			userFacingHandler = new ConcurrentWorker(defaultHandler);
			final Function<ClientRequest, JSONObject> rpcHandler = userFacingHandler.createRpcRequestHandler();
			new Thread(() -> {
				final AtomicBoolean connectionIsAlive = new AtomicBoolean(true);
				new IpcReader(System.in) {
//					protected void handleByte(byte b) {
//						super.handleByte(b);
//						flog("worker got byte: " + b);
//
//					}
					// TODO funnel messages to handler

					protected void onMessage(String msg) {
						flog("worker input: " + msg);
						JSONObject obj;
						try {
							obj = new JSONObject(msg);
						} catch (JSONException e) {
							flog("worker non-json input: " + e);
							System.out.println("Got non-json message to worker: " + msg);
							e.printStackTrace();
							return;
						}
						writeToCoordinator.accept(
								rpcHandler.apply(new ClientRequest(obj, writeToCoordinator, connectionIsAlive)));
					}
				}.runForever();
			}).start();
			return; // not break, avoid starting websocket/http servers
		}

		case DISABLED:
			// Fall-through
		default:
			userFacingHandler = defaultHandler;
			break;
		}
		if (parsedArgs.runTest) {
			final MergedResult res = RunAllTests.run(new TestClient(userFacingHandler),
					parsedArgs.concurrencyMode != ConcurrencyMode.DISABLED);
//			userFacingHandler.shutdown();
			System.exit(res == MergedResult.ALL_PASS ? 0 : 1);

			return;
		}

		// First access to compat methods causes info messages to appear in the
		// terminal. Do it early to inform user.
		CodespacesCompat.shouldApplyCompatHacks();
		CodespacesCompat.getChangeBufferTime();

		final ServerToClientMessagePusher msgPusher = new ServerToClientMessagePusher();
		final Function<ClientRequest, JSONObject> reqHandler = userFacingHandler.createRpcRequestHandler();
		final Runnable onSomeClientDisconnected = userFacingHandler::onOneOrMoreClientsDisconnected;
		new Thread(() -> WebServer.start(parsedArgs, msgPusher, reqHandler, onSomeClientDisconnected)).start();
		if (!WebSocketServer.shouldDelegateWebsocketToHttp()) {
			new Thread(() -> WebSocketServer.start(parsedArgs, msgPusher, reqHandler, onSomeClientDisconnected)).start();
		} else {
			System.out.println("Not starting websocket server, running requests over normal HTTP requests instead.");
		}

		new FileMonitor(new File(parsedArgs.jarPath)) {
			public void onChange() {
				System.out.println("Jar changed!");
				msgPusher.onJarChange();
			};
		}.start();
	}
}
