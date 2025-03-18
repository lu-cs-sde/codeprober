package protocolgen.spec;

import org.json.JSONObject;

@SuppressWarnings("unused")
public class PutWorkspaceMetadata extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "PutWorkspaceMetadata";
			public final Object path = String.class;
			public final Object metadata = opt(JSONObject.class);
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object ok = Boolean.class;
		};
	}

}
