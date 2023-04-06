package protocolgen.spec;

import org.json.JSONObject;

@SuppressWarnings("unused")
public class TunneledWsPutRequest extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "wsput:tunnel";
			public final Object session = String.class;
			public final Object request = JSONObject.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object response = JSONObject.class;
		};
	}

}
