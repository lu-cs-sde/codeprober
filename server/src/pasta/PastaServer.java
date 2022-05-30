package pasta;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import pasta.rpc.JsonRequestHandler;
import pasta.server.WebServer;
import pasta.server.WebSocketServer;
import pasta.util.FileMonitor;

//	/Users/anton/repo/extendj/java8/extendj.jar -XparseOnly
//	/Users/anton/repo/lth/edan65/A6-SimpliC/compiler.jar
//  /Users/anton/repo/jastadd-fj/fj-compiler.jar
//  /Users/anton/repo/chocopy-ellen-samer/compiler.jar

public class PastaServer {

	public static void printUsage() {
		System.out.println(
				"Usage: java -jar pasta-server.jar path/to/your/compiler.jar [args-to-forward-to-compiler-on-each-request]");
	}

	public static void main(String[] mainArgs) {
		System.out.println("Starting debug build..");
		if (mainArgs.length == 0) {
			printUsage();
			System.exit(1);
		}
		final String jarPath = mainArgs[0];
		final JsonRequestHandler handler = new DefaultRequestHandler(jarPath,
				Arrays.copyOfRange(mainArgs, 1, mainArgs.length));

		new Thread(WebServer::start).start();

		final List<Runnable> onJarChangeListeners = Collections.<Runnable>synchronizedList(new ArrayList<>());
		new Thread(() -> WebSocketServer.start(onJarChangeListeners, handler.createRpcRequestHandler())).start();

		System.out.println("Starting file monitor...");
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
