package codeprober;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.json.JSONObject;

import codeprober.rpc.JsonRequestHandler;
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
		final String jarPath = mainArgs[0];
		final JsonRequestHandler handler = new DefaultRequestHandler(jarPath,
				Arrays.copyOfRange(mainArgs, 1, mainArgs.length));

		final Function<JSONObject, String> reqHandler = handler.createRpcRequestHandler();
		new Thread(() -> WebServer.start(reqHandler)).start();

		final List<Runnable> onJarChangeListeners = Collections.<Runnable>synchronizedList(new ArrayList<>());
		new Thread(() -> { WebSocketServer.start(onJarChangeListeners, reqHandler); }).start();

		new FileMonitor(new File(jarPath)) {
			public void onChange() {
				System.out.println("Jar changed!");
				synchronized (onJarChangeListeners) {
					onJarChangeListeners.forEach(Runnable::run);
				}
			};
		}.start();
	}
}
