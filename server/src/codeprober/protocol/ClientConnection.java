package codeprober.protocol;

import org.json.JSONObject;

public interface ClientConnection {
	void sendAsyncMessage(JSONObject message);
	void setOnDisconnectListener(Runnable onDisconnect);
}
