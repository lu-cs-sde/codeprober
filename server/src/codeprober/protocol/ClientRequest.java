package codeprober.protocol;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.json.JSONObject;

import codeprober.protocol.data.AsyncRpcUpdate;

public class ClientRequest {

	public final JSONObject data;
	private final Consumer<AsyncRpcUpdate> asyncResponseConsumer;
	public final AtomicBoolean connectionIsAlive;

	public ClientRequest(JSONObject data, Consumer<AsyncRpcUpdate> asyncResponseConsumer, AtomicBoolean connectionIsAlive) {
		this.data = data;
		this.asyncResponseConsumer = asyncResponseConsumer;
		this.connectionIsAlive = connectionIsAlive;
	}

	public void sendAsyncResponse(AsyncRpcUpdate message) {
		asyncResponseConsumer.accept(message);;
	}
}
