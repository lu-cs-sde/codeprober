package protocolgen.spec;

import org.json.JSONObject;

@SuppressWarnings("unused")
public class TopRequest extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "rpc";
			public final Object id = Long.class;
			public final Object data = JSONObject.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object type = "rpc";
			public final Object id = Long.class;
			public final Object data = TopRequestResponseData.class;
		};
	}

	public static class TopRequestResponseData extends StreamableUnion {
		public final Object success = JSONObject.class;
		public final Object failureMsg = String.class;
	}
}
