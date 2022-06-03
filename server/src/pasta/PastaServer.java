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
//  /Users/anton/repo/extendj/java8/extendj.jar -XparseOnly -cp /Users/anton/eclipse-workspace/Pasta/server/libs/json.jar /Users/anton/eclipse-workspace/Pasta/server/src/pasta/metaprogramming/Reflect.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/metaprogramming/StreamInterceptor.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/metaprogramming/PositionRepresentation.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/metaprogramming/StdIoInterceptor.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/metaprogramming/InvokeProblem.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/AstInfo.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/locator/TypeAtLoc.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/locator/ParameterizedNtaEdge.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/locator/NodeEdge.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/locator/CreateLocator.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/locator/ChildIndexEdge.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/locator/AttrsInNode.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/locator/NodesAtPosition.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/locator/ApplyLocator.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/locator/Span.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/locator/TypeAtLocEdge.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/util/FileMonitor.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/util/MagicStdoutMessageParser.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/util/CompilerClassLoader.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/util/ASTProvider.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/util/SystemExitControl.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/util/BenchmarkTimer.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/server/WebSocketServer.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/server/WebServer.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/protocol/ParameterType.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/protocol/decode/DecodeValue.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/protocol/AstCacheStrategy.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/protocol/PositionRecoveryStrategy.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/protocol/ParameterValue.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/protocol/create/EncodeResponseValue.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/protocol/create/CreateType.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/protocol/create/CreateValue.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/PastaServer.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/DefaultRequestHandler.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/ast/AstNode.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/ast/AstParentException.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/ast/AstLoopException.java /Users/anton/eclipse-workspace/Pasta/server/src/pasta/rpc/JsonRequestHandler.java

public class PastaServer {

	public static void printUsage() {
		System.out.println(
				"Usage: java -jar pasta-server.jar path/to/your/analyzer-or-compiler.jar [args-to-forward-to-compiler-on-each-request]");
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
