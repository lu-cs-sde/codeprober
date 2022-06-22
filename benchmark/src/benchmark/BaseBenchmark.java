package benchmark;

import java.util.function.Consumer;

import org.json.JSONObject;

public abstract class BaseBenchmark {

	public final Consumer<String> postMessage;

	public BaseBenchmark(Consumer<String> postMessage) {
		this.postMessage = postMessage;
	}
	
	
	public abstract void handleIncomingMessage(JSONObject msg);
	public abstract void run() throws InterruptedException;
}
