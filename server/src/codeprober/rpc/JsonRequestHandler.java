package codeprober.rpc;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.json.JSONObject;

@FunctionalInterface
public interface JsonRequestHandler {

	static final Object lock = new Object();

	JSONObject handleRequest(JSONObject queryObj, Consumer<JSONObject> sendAsyncMessage);

	default BiFunction<JSONObject, Consumer<JSONObject>, JSONObject> createRpcRequestHandler() {
		return (obj, sendAsyncMessage) -> {
			final long start = System.nanoTime();

			JSONObject rpcResponse = new JSONObject();
			rpcResponse.put("type", "rpc");
			rpcResponse.put("id", obj.getLong("id"));
			try {
				synchronized (lock) {
					final JSONObject encoded = handleRequest(obj, sendAsyncMessage);
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
