package codeprober;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.json.JSONException;
import org.json.JSONObject;

import codeprober.RunAllTests.MergedResult;
import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.rpc.JsonRequestHandler;
import codeprober.server.CodespacesCompat;
import codeprober.server.ServerToClientMessagePusher;
import codeprober.server.WebServer;
import codeprober.server.WebSocketServer;
import codeprober.toolglue.UnderlyingTool;
import codeprober.util.FileMonitor;
import codeprober.util.VersionInfo;

public class CodeProber {

	private static enum ConcurrencyMode {
		DISABLED, COORDINATOR, WORKER,
	}

	public static void printUsage() {
		System.out.println(
				"Usage: java -jar code-prober.jar [--test] path/to/your/analyzer-or-compiler.jar [args-to-forward-to-your-main]");
	}

	static void flog(String msg) {
//		try {
//			Files.write(new File("/Users/anton/eclipse-workspace/Pasta/log").toPath(), (msg + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	public static void main(String[] mainArgs) throws IOException {
		System.out.println("Starting server, version: " + VersionInfo.getInstance().toString() + "..");
		if (mainArgs.length == 0) {
			printUsage();
			System.exit(1);
		}

		boolean runTests = false;
		AtomicReference<ConcurrencyMode> concurrency = new AtomicReference<>(ConcurrencyMode.DISABLED);
		final Consumer<ConcurrencyMode> setConcurrencyMode = (mode -> {
			if (concurrency.get() != ConcurrencyMode.DISABLED) {
				throw new IllegalArgumentException(
						"Can only specify either '--concurrent', '--concurrent=[workerCount]' or '--worker', not multiple options simultaneously");
			}
			;
			concurrency.set(mode);
		});

		String jarPath = null;
		String[] extraArgs = null;
		Integer workerCount = null;
		gatherArgs: for (int i = 0; i < mainArgs.length; ++i) {
			switch (mainArgs[i]) {
			case "--test": {
				if (runTests) {
					throw new IllegalArgumentException("Duplicate '--test'");
				}
				runTests = true;
				break;
			}
			case "--concurrent": {
				setConcurrencyMode.accept(ConcurrencyMode.COORDINATOR);
				break;
			}
			case "--worker": {

				setConcurrencyMode.accept(ConcurrencyMode.WORKER);
				break;
			}

			default: {
				if (mainArgs[i].startsWith("--concurrent=")) {
					setConcurrencyMode.accept(ConcurrencyMode.COORDINATOR);
					try {
						workerCount = Integer.parseInt(mainArgs[i].substring("--concurrent=".length()));
						if (workerCount <= 0) {
							throw new IllegalArgumentException("Minimum worker count is 1, got '" + workerCount + "'");
						}
					} catch (NumberFormatException e) {
						System.out.println("Invalid value for '--concurrent'");
						e.printStackTrace();
						System.exit(1);
					}
				} else {
					jarPath = mainArgs[i];
					extraArgs = Arrays.copyOfRange(mainArgs, i + 1, mainArgs.length);
					break gatherArgs;
				}
			}
			}
		}
		if (jarPath == null) {
			printUsage();
			System.exit(1);
		}

		final JsonRequestHandler defaultHandler = new DefaultRequestHandler(UnderlyingTool.fromJar(jarPath), extraArgs);
		final JsonRequestHandler userFacingHandler;
		flog(Arrays.toString(mainArgs));

		switch (concurrency.get()) {

		case COORDINATOR: {
			try {
				userFacingHandler = new ConcurrentCoordinator(defaultHandler, jarPath, extraArgs, workerCount);
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
			final BiFunction<JSONObject, Consumer<JSONObject>, JSONObject> rpcHandler = userFacingHandler
					.createRpcRequestHandler();
			new Thread(() -> {
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
						writeToCoordinator.accept(rpcHandler.apply(obj, writeToCoordinator));
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
		if (runTests) {
			final MergedResult res = RunAllTests.run(new TestClient(userFacingHandler),
					concurrency.get() != ConcurrencyMode.DISABLED);
//			userFacingHandler.shutdown();
			System.exit(res == MergedResult.ALL_PASS ? 0 : 1);

			return;
		}

		// First access to compat methods causes info messages to appear in the
		// terminal. Do it early to inform user.
		CodespacesCompat.shouldApplyCompatHacks();
		CodespacesCompat.getChangeBufferTime();

		final ServerToClientMessagePusher msgPusher = new ServerToClientMessagePusher();
		final BiFunction<JSONObject, Consumer<JSONObject>, JSONObject> reqHandler = userFacingHandler
				.createRpcRequestHandler();
		new Thread(() -> WebServer.start(msgPusher, reqHandler)).start();
		if (!WebSocketServer.shouldDelegateWebsocketToHttp()) {
			new Thread(() -> WebSocketServer.start(msgPusher, reqHandler)).start();
		} else {
			System.out.println("Not starting websocket server, running requests over normal HTTP requests instead.");
		}

		new FileMonitor(new File(jarPath)) {
			public void onChange() {
				System.out.println("Jar changed!");
				msgPusher.onJarChange();
			};
		}.start();
	}
}
