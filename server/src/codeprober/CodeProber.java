package codeprober;

import java.io.File;
import java.util.Arrays;
import java.util.function.Function;

import org.json.JSONObject;

import codeprober.rpc.JsonRequestHandler;
import codeprober.server.CodespacesCompat;
import codeprober.server.ServerToClientMessagePusher;
import codeprober.server.WebServer;
import codeprober.server.WebSocketServer;
import codeprober.util.FileMonitor;
import codeprober.util.VersionInfo;

public class CodeProber {

	public static void printUsage() {
		System.out.println(
				"Usage: java -jar code-prober.jar path/to/your/analyzer-or-compiler.jar [args-to-forward-to-your-main]");
	}

	public static void main(String[] mainArgs) {
		System.out.println("Starting server, version: " + VersionInfo.getInstance().toString() + "..");
		if (mainArgs.length == 0) {
			printUsage();
			System.exit(1);
		}

		CodespacesCompat.shouldApplyCompatHacks(); // First access causes info messages to appear in the terminal. Do it early to inform user.
		CodespacesCompat.getChangeBufferTime(); // -||-

		final String jarPath = mainArgs[0];
		final JsonRequestHandler handler = new DefaultRequestHandler(jarPath,
				Arrays.copyOfRange(mainArgs, 1, mainArgs.length));

		final ServerToClientMessagePusher msgPusher = new ServerToClientMessagePusher();
		final Function<JSONObject, String> reqHandler = handler.createRpcRequestHandler();
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
