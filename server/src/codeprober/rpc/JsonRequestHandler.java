package codeprober.rpc;

import java.util.function.Function;

import org.json.JSONObject;

import codeprober.protocol.ClientRequest;
import codeprober.protocol.data.TopRequestReq;
import codeprober.protocol.data.TopRequestRes;
import codeprober.protocol.data.TopRequestResponseData;
import codeprober.server.WebServer;

@FunctionalInterface
public interface JsonRequestHandler {

	static final Object lock = new Object();

	JSONObject handleRequest(ClientRequest request);

	default void onOneOrMoreClientsDisconnected() {
		/**
		 * Default: noop. Override for request handlers with queues and/or long-lasting
		 * requests that are rendered obsolete due to the disconnects. Check disconnect
		 * status in {@link ClientRequest#connectionIsAlive}.
		 */
	}

	public static Function<ClientRequest, JSONObject> createTopRequestHandler(Function<ClientRequest, JSONObject> unwrappedHandler) {
		return (request) -> {
			final long start = System.nanoTime();

			final TopRequestReq parsed = TopRequestReq.fromJSON(request.data);

			TopRequestResponseData response;
			try {
				final JSONObject encoded = unwrappedHandler.apply(
						// Strip away data wrapper
						new ClientRequest(parsed.data, //
								request::sendAsyncResponse, request.connectionIsAlive));
				response = TopRequestResponseData.fromSuccess(encoded);
			} catch (RuntimeException e) {
				System.out.println("Request threw an error");
				e.printStackTrace();
				response = TopRequestResponseData
						.fromFailureMsg("Error while processing request. See server for more info");
			}

      if (WebServer.DEBUG_REQUESTS) {
        System.out.printf("Handled request in %.2fms\n", (System.nanoTime() - start) / 1_000_000.0);
      }
			return new TopRequestRes(parsed.id, response).toJSON();

		};
	}
}
