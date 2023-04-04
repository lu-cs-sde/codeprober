package codeprober.protocol;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.json.JSONObject;

public class ClientRequest {

	public final JSONObject data;
	private final Consumer<JSONObject> asyncResponseConsumer;
	public final AtomicBoolean connectionIsAlive;

	public ClientRequest(JSONObject data, Consumer<JSONObject> asyncResponseConsumer, AtomicBoolean connectionIsAlive) {
		this.data = data;
		this.asyncResponseConsumer = asyncResponseConsumer;
		this.connectionIsAlive = connectionIsAlive;
	}

	public void sendAsyncResponse(JSONObject message) {
		asyncResponseConsumer.accept(message);;
	}
}
