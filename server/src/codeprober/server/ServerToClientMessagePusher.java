package codeprober.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Class helping with pushing messages from the server to the client. Over
 * websockets, this is a native feature. <br>
 * Over http, this is implemented with long polling.
 */
public class ServerToClientMessagePusher {
	private final List<Consumer<ServerToClientEvent>> onChangeListeners;
	private int eventCounter;

	public ServerToClientMessagePusher() {
		this.onChangeListeners = Collections.<Consumer<ServerToClientEvent>>synchronizedList(new ArrayList<>());
		this.eventCounter = 1;
	}

	public void onChange(ServerToClientEvent event) {
		this.eventCounter++;
		synchronized (onChangeListeners) {
			onChangeListeners.forEach(l -> l.accept(event));
		}
		synchronized (this) {
			notifyAll();
		}
	}

	public int getEventCounter() {
		return eventCounter;
	}

	public void addChangeListener(Consumer<ServerToClientEvent> r) {
		onChangeListeners.add(r);
	}

	public void removeChangeListener(Consumer<ServerToClientEvent> r) {
		onChangeListeners.remove(r);
	}
}
