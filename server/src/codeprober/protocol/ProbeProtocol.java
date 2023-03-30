package codeprober.protocol;

import org.json.JSONArray;
import org.json.JSONObject;

public class ProbeProtocol extends ProtocolBuilder {

	// Query
	public static final String type = "query";
	public static final Entry<String> positionRecovery = str("posRecovery");
	public static final Entry<String> cache = str("cache");
	public static final Entry<String> text = str("text");
	public static final Entry<Boolean> captureStdout = bool("stdout");
	public static final Entry<JSONArray> mainArgs = arr("mainArgs");
	public static final Entry<String> tmpSuffix = str("tmpSuffix");
	public static final Entry<JSONObject> query = obj("query");

	public static class Query {
		public static final Entry<JSONObject> attribute = obj("attr");
		public static final Entry<JSONObject> locator = obj("locator");
	}

	public static class Attribute {
		public static final Entry<String> name = str("name");
		public static final Entry<JSONArray> args = arr("args");
	}

	// Response
	public static final Entry<JSONArray> body = arr("body");
}
