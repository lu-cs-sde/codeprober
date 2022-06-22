package benchmark;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONException;
import org.json.JSONObject;

public class Run {

	public static void main(String[] args) {
		System.out.println("Connecting to localhost:8080...");
//		HttpClient.newBuilder().
//		WebSocket.bu
		HttpClient client = HttpClient.newHttpClient();

		final AtomicReference<BaseBenchmark> callback = new AtomicReference<>(null);
		CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
				.buildAsync(URI.create("ws://localhost:8080"), new Listener() {

					StringBuilder textBuilder = new StringBuilder();

					@Override
					public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
//						System.out.println("onText " + last + " : " + data);
						textBuilder.append(data.toString());
						if (last) {
							final String mergedText = textBuilder.toString();
							textBuilder = new StringBuilder();
							final BaseBenchmark cb = callback.get();
							if (cb != null) {
								try {
									cb.handleIncomingMessage(new JSONObject(mergedText));
								} catch (JSONException e) {
									System.out.println("Non-JSON message received.. " + e);
									e.printStackTrace();
								}
							}
						}
						return Listener.super.onText(webSocket, data, last);
					}

					@Override
					public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
						System.out.println("onBinary " + data);
						return Listener.super.onBinary(webSocket, data, last);
					}

					@Override
					public void onOpen(WebSocket webSocket) {
						System.out.println("Connected!");
						Listener.super.onOpen(webSocket);
					}

					@Override
					public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
						System.out.println("onClose");
						return Listener.super.onClose(webSocket, statusCode, reason);
					}

					@Override
					public void onError(WebSocket webSocket, Throwable error) {
						System.out.println("onError " + error);
						error.printStackTrace();
						Listener.super.onError(webSocket, error);
					}

					@Override
					public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
						System.out.println("onPing");
						return Listener.super.onPing(webSocket, message);
					}

					@Override
					public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
						System.out.println("onPong");
						return Listener.super.onPong(webSocket, message);
					}
				});

		final WebSocket ws = wsFuture.join();

		callback.set(new ExtendJBenchmark(msg -> ws.sendText(msg, true)));
		try {
			callback.get().run();
		} catch (Exception e1) {
			System.out.println("Benchmark failed");
			e1.printStackTrace();
		}
//		
//		try {
//			Thread.sleep(1000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
}
