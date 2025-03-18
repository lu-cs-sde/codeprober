package protocolgen.spec;

import org.json.JSONObject;

@SuppressWarnings("unused")
public class GetWorkspaceFile extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "GetWorkspaceFile";
			public final Object path = String.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object content = opt(String.class);
			public final Object metadata = opt(JSONObject.class);
		};
	}

}
