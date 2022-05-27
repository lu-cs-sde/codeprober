package pasta.rpc;

import java.util.function.Function;

import org.json.JSONObject;

@FunctionalInterface
public interface JsonRequestHandler {
	
	static  final Object lock = new Object();

	JSONObject handleRequest(JSONObject queryObj);

	default Function<JSONObject, String> createRpcRequestHandler() {
		return obj -> {
			
			JSONObject rpcResponse = new JSONObject();
			rpcResponse.put("type", "rpc");
			rpcResponse.put("id", obj.getLong("id"));
			try {
				synchronized (lock) {
					final JSONObject encoded = handleRequest(obj);
					rpcResponse.put("result", encoded == null ? JSONObject.NULL : encoded);
				}
			} catch (RuntimeException e) {
				e.printStackTrace();
				rpcResponse.put("error", "Error while processing request. See server for more info");
			}
			return rpcResponse.toString();
		};
	}
}
