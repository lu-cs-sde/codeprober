package codeprober.rpc;

import java.util.function.Consumer;
import java.util.function.Function;

import org.json.JSONObject;

import codeprober.protocol.ClientRequest;

@FunctionalInterface
public interface JsonRequestHandler {

	static final Object lock = new Object();

	JSONObject handleRequest(ClientRequest request);

	default void onOneOrMoreClientsDisconnected() {
		/**
		 * Default: noop. Override for request handlers with queues and/or long-lasting
		 * requests thare are rendered obsolete due to the disconnects. Check disconnect
		 * status in {@link ClientRequest#connectionIsAlive}.
		 */
	}

	default Function<ClientRequest, JSONObject> createRpcRequestHandler() {
		return (request) -> {
			final long start = System.nanoTime();

			JSONObject rpcResponse = new JSONObject();
			rpcResponse.put("type", "rpc");
			rpcResponse.put("id", request.data.getLong("id"));
			try {
				synchronized (lock) {
					final JSONObject encoded = handleRequest(request);
					rpcResponse.put("result", encoded == null ? JSONObject.NULL : encoded);
				}
			} catch (RuntimeException e) {
				System.out.println("Request threw an error");
				e.printStackTrace();
				rpcResponse.put("error", "Error while processing request. See server for more info");
			}
			System.out.printf("Handled request in %.2fms\n", (System.nanoTime() - start) / 1_000_000.0);
			return rpcResponse;
		};
	}
}
