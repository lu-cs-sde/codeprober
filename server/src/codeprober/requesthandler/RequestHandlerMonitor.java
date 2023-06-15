package codeprober.requesthandler;

import java.util.function.Function;

import org.json.JSONObject;

import codeprober.protocol.ClientRequest;

public class RequestHandlerMonitor {

	private final Function<ClientRequest, JSONObject> requestHandler;

	private ClientRequest pendingRequest;
	private JSONObject pendingResponse;

	private Thread processThread;

	public RequestHandlerMonitor(Function<ClientRequest, JSONObject> requestHandler) {
		this.requestHandler = requestHandler;
	}

	public synchronized JSONObject submit(ClientRequest req) {
		if (processThread == null) {
			processThread = new Thread(() -> {
				while (true) {
					try {
						process();
					} catch (InterruptedException e) {
						System.out.println("Request monitor thread interrupted");
						e.printStackTrace();
						return; // Honor the interruption by exiting
					}
				}
			});
			processThread.start();
		}

		while (pendingRequest != null || pendingResponse != null) {
			try {
				wait();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		pendingRequest = req;
		notifyAll();
		while (pendingResponse == null) {
			try {
				wait();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		final JSONObject ret = pendingResponse;
		pendingResponse = null;
		notifyAll();
		return ret;
	}

	private synchronized void process() throws InterruptedException {
		while (pendingRequest == null) {
			wait();
		}
		pendingResponse = requestHandler.apply(pendingRequest);
		pendingRequest = null;
		notifyAll();
	}
}
