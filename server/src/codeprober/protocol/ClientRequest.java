package codeprober.protocol;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.json.JSONObject;

import codeprober.protocol.data.AsyncRpcUpdate;
import codeprober.server.WorkspacePathFilteringUpdateListener;

public class ClientRequest {

	public final JSONObject data;
	private final Consumer<AsyncRpcUpdate> asyncResponseConsumer;
	public final AtomicBoolean connectionIsAlive;
	public final Consumer<String> onDidUpdateWorkspacePath;

	public ClientRequest(JSONObject data, Consumer<AsyncRpcUpdate> asyncResponseConsumer,
			AtomicBoolean connectionIsAlive, Consumer<String> onDidUpdateWorkspacePath) {
		this.data = data;
		this.asyncResponseConsumer = asyncResponseConsumer;
		this.connectionIsAlive = connectionIsAlive;
		this.onDidUpdateWorkspacePath = onDidUpdateWorkspacePath;
	}

	public ClientRequest(JSONObject data, Consumer<AsyncRpcUpdate> asyncResponseConsumer,
			AtomicBoolean connectionIsAlive, WorkspacePathFilteringUpdateListener listener) {
		this(data, asyncResponseConsumer, connectionIsAlive, listener::onWorkspacePathChanged);
	}

	public void sendAsyncResponse(AsyncRpcUpdate message) {
		asyncResponseConsumer.accept(message);
	}
}
