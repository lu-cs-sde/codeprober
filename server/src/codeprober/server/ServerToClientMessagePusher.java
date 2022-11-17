package codeprober.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class helping with pushing messages from the server to the client. Over
 * websockets, this is a native feature. <br>
 * Over http, this is implemented with long polling.
 */
public class ServerToClientMessagePusher {
	private final List<Runnable> onJarChangeListeners;
	private int eventCounter;

	public ServerToClientMessagePusher() {
		this.onJarChangeListeners = Collections.<Runnable>synchronizedList(new ArrayList<>());
		this.eventCounter = 1;
	}

	public void onJarChange() {
		this.eventCounter++;
		synchronized (onJarChangeListeners) {
			onJarChangeListeners.forEach(Runnable::run);
		}
		synchronized (this) {
			notifyAll();
		}
	}

	public synchronized int pollEvent(int prevEventCtr) {
		if (prevEventCtr == eventCounter) {
			try {
				// 5 minutes
				wait(5 * 60 * 1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("pollEvent done, " + prevEventCtr + " -> " + eventCounter);
		return eventCounter;
	}

	public void addJarChangeListener(Runnable r) {
		onJarChangeListeners.add(r);
	}

	public void removeJarChangeListener(Runnable r) {
		onJarChangeListeners.remove(r);
	}
}
