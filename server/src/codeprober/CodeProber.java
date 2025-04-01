package codeprober;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.json.JSONException;
import org.json.JSONObject;

import codeprober.RunAllTests.MergedResult;
import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.metaprogramming.StreamInterceptor.OtherThreadDataHandling;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.data.RequestAdapter;
import codeprober.protocol.data.TopRequestReq;
import codeprober.protocol.data.TopRequestRes;
import codeprober.protocol.data.TopRequestResponseData;
import codeprober.protocol.data.TunneledWsPutRequestReq;
import codeprober.protocol.data.TunneledWsPutRequestRes;
import codeprober.requesthandler.RequestHandlerMonitor;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;
import codeprober.server.BackingFileSettings;
import codeprober.server.CodespacesCompat;
import codeprober.server.ServerToClientEvent;
import codeprober.server.ServerToClientMessagePusher;
import codeprober.server.WebServer;
import codeprober.server.WebSocketServer;
import codeprober.server.WorkspaceApi;
import codeprober.toolglue.UnderlyingTool;
import codeprober.toolglue.UnderlyingToolProxy;
import codeprober.util.DirectoryMonitor;
import codeprober.util.FileMonitor;
import codeprober.util.ParsedArgs;
import codeprober.util.ParsedArgs.ConcurrencyMode;
import codeprober.util.RunWorkspaceTest;
import codeprober.util.SessionLogger;
import codeprober.util.VersionInfo;
import codeprober.util.WorkspaceDirectoryMonitor;

public class CodeProber {

	public static void flog(String msg) {
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
		final ParsedArgs parsedArgs = ParsedArgs.parse(mainArgs);

		final SessionLogger sessionLogger = SessionLogger.init();
		if (sessionLogger != null) {
			System.out.println("Logging anonymous session data to " + sessionLogger.getTargetPath());
			sessionLogger.log(new JSONObject() //
					.put("t", "Startup") //
					.put("v", VersionInfo.getInstance().toString()));
		}
		final UnderlyingToolProxy underlyingTool = new UnderlyingToolProxy();
		if (parsedArgs.jarPath != null) {
			underlyingTool.setProxyTarget(UnderlyingTool.fromJar(parsedArgs.jarPath));
		}
		final JsonRequestHandler defaultHandler = new DefaultRequestHandler(underlyingTool, parsedArgs.extraArgs,
				sessionLogger);
		final JsonRequestHandler userFacingHandler;
		flog(Arrays.toString(mainArgs));

		final ServerToClientMessagePusher msgPusher = new ServerToClientMessagePusher();
		final File backingFile = BackingFileSettings.getRealFileToBeUsedInRequests();
		if (backingFile != null) {
			if (parsedArgs.concurrencyMode != ConcurrencyMode.DISABLED) {
				System.err
						.println("Illegal mix of backing files and concurrency modes, you can only use one at a time.");
				System.err.println(
						"This is because concurrent evaluations would necessarily need to write their sources to the same file, which is not thread safe");
				System.err.println("Exiting.");
				System.exit(1);
			}
			System.out.println("Using backing file " + backingFile);
			System.out.println(
					"CAUTION: Any edits made inside CodeProber will immediately be saved to that file. Make sure important source files are in git.");
			if (!backingFile.exists()) {
				System.out.println("Backing file doesn't exist, initializing to an empty file");
				backingFile.createNewFile();
			}

			BackingFileSettings
					.monitorBackingFileChanges(() -> msgPusher.onChange(ServerToClientEvent.BACKING_FILE_CHANGED));
		}

		switch (parsedArgs.concurrencyMode) {
		case COORDINATOR: {
			if (parsedArgs.jarPath == null) {
				System.err.println("Illegal mix of --concurrent and not specifying underlying tool.");
				System.exit(1);
			}
			StdIoInterceptor.tag = "Coordinator";
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
			StdIoInterceptor.tag = "Worker";
			final PrintStream realOut = System.out;

			final StdIoInterceptor io = new StdIoInterceptor(false, OtherThreadDataHandling.MERGE) {

				@Override
				public void onLine(boolean stdout, String line) {
					flog(line);
				}
			};
			io.install();
			final Consumer<JSONObject> writeToCoordinator = msgObj -> {
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
			final Function<ClientRequest, JSONObject> rpcHandler = JsonRequestHandler
					.createTopRequestHandler(userFacingHandler::handleRequest);
			new Thread(() -> {
				final AtomicBoolean connectionIsAlive = new AtomicBoolean(true);
				new IpcReader(System.in) {

					protected void onMessage(String msg) {
						JSONObject obj;
						try {
							obj = new JSONObject(msg);
						} catch (JSONException e) {
							flog("worker non-json input: " + e);
							System.out.println("Got non-json message to worker: " + msg);
							e.printStackTrace();
							return;
						}
						writeToCoordinator.accept(rpcHandler.apply(new ClientRequest(obj,
								asyncMsg -> writeToCoordinator.accept(asyncMsg.toJSON()), connectionIsAlive)));
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
			File workspace = WorkspaceHandler.getWorkspaceRoot(false);
			if (workspace != null) {
				// Test files in workspace
				System.exit(RunWorkspaceTest
						.run(userFacingHandler) == codeprober.util.RunWorkspaceTest.MergedResult.ALL_PASS ? 0 : 1);
			} else {
				// Run (legacy) tests inside the cpr.testDir
				final MergedResult res = RunAllTests.run(new TestClient(userFacingHandler),
						parsedArgs.concurrencyMode != ConcurrencyMode.DISABLED);
//			userFacingHandler.shutdown();
				System.exit(res == MergedResult.ALL_PASS ? 0 : 1);
			}

			return;
		}
		if (parsedArgs.oneshotRequest != null) {
			final JSONObject responseObj = new RequestAdapter() {

				@Override
				protected TopRequestRes handleTopRequest(TopRequestReq req) {
					final JSONObject resp = this.handle(req.data);
					return new TopRequestRes(req.id, TopRequestResponseData.fromSuccess(resp));
				}

				@Override
				protected TunneledWsPutRequestRes handleTunneledWsPutRequest(TunneledWsPutRequestReq req) {
					final ClientRequest cr = new ClientRequest(req.request, asyncMsg -> {
						throw new IllegalStateException("Async message in oneshot request");
					}, new AtomicBoolean(true));

					final JSONObject resp = userFacingHandler.handleRequest(cr);
					return new TunneledWsPutRequestRes(resp);
				}

			}.handle(new JSONObject(parsedArgs.oneshotRequest));

			Files.write(parsedArgs.oneshotOutput.toPath(), responseObj.toString().getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			System.out.println("Wrote result to " + parsedArgs.oneshotOutput);
			System.exit(0);
			return;
		}

		// First access to compat methods causes info messages to appear in the
		// terminal. Do it early to inform user.
		CodespacesCompat.shouldApplyCompatHacks();
		CodespacesCompat.getChangeBufferTime();
		final File workspaceRoot = WorkspaceApi.getWorkspaceRoot(true);
		if (workspaceRoot != null) {
			new WorkspaceDirectoryMonitor(workspaceRoot, msgPusher).start();
		}

		final RequestHandlerMonitor monitor = new RequestHandlerMonitor(userFacingHandler::handleRequest);
		final Function<ClientRequest, JSONObject> unwrappedHandler = monitor::submit;
		final Function<ClientRequest, JSONObject> topHandler = JsonRequestHandler
				.createTopRequestHandler(unwrappedHandler);

		final AtomicReference<FileMonitor> lastMonitor = new AtomicReference<>(null);
		final Consumer<String> monitorPath = (jarPath) -> {
			if (lastMonitor.get() != null) {
				lastMonitor.getAndSet(null).stopThread();
			}
			final File jarFile = new File(jarPath);
			if (!jarFile.exists()) {
				System.err.println("⚠ Invalid jar path '" + jarPath
						+ "'. No such file exists. Please restart with a new path, or create the file before trying to create a probe.");
			} else if (!jarFile.isFile()) {
				System.err.println("⚠ Invalid jar path '" + jarPath
						+ "'. It is not a file. Please restart with a new path.");
			}
			final FileMonitor fm = new FileMonitor(jarFile) {
				public void onChange() {
					System.out.println("Jar changed!");
					if (sessionLogger != null) {
						sessionLogger.log(new JSONObject() //
								.put("t", "Refresh"));
					}
					msgPusher.onChange(ServerToClientEvent.JAR_CHANGED);
				};
			};
			lastMonitor.set(fm);
			fm.start();
		};
		final String extraFileMonitorPath = System.getProperty("cpr.extraFileMonitorDir", null);
		if (extraFileMonitorPath != null) {
			final DirectoryMonitor fm = new DirectoryMonitor(new File(extraFileMonitorPath)) {
				public void onChange() {
					System.out.println("Extra monitor dir changed!");
					if (sessionLogger != null) {
						sessionLogger.log(new JSONObject() //
								.put("t", "Refresh"));
					}
					msgPusher.onChange(ServerToClientEvent.JAR_CHANGED);
				};
			};
			fm.start();
		}
		final AtomicBoolean needsTool = new AtomicBoolean(parsedArgs.jarPath == null);
		final Consumer<String> setUnderlyingJarPath = (jarPath) -> {
			needsTool.set(false);
			underlyingTool.setProxyTarget(UnderlyingTool.fromJar(jarPath));
			monitorPath.accept(jarPath);
		};
		if (parsedArgs.jarPath != null) {
			monitorPath.accept(parsedArgs.jarPath);
		}

		final Runnable onSomeClientDisconnected = userFacingHandler::onOneOrMoreClientsDisconnected;
		new Thread(() -> WebServer.start(parsedArgs, msgPusher, unwrappedHandler, onSomeClientDisconnected, needsTool,
				setUnderlyingJarPath, sessionLogger)).start();
		if (!WebSocketServer.shouldDelegateWebsocketToHttp() && WebSocketServer.getPort() != WebServer.getPort()) {
			new Thread(() -> WebSocketServer.start(parsedArgs, msgPusher, topHandler, onSomeClientDisconnected,
					sessionLogger)).start();
		} else if (WebSocketServer.shouldDelegateWebsocketToHttp()) {
			System.out.println("Not starting websocket server, running requests over normal HTTP requests instead.");
		} else {
			// WebSocket port is same as HTTP port. No need to print anything
		}
	}
}
