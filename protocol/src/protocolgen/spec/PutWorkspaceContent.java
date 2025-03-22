package protocolgen.spec;

import org.json.JSONObject;

@SuppressWarnings("unused")
public class PutWorkspaceContent extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "PutWorkspaceContent";
			public final Object path = String.class;
			public final Object content = String.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object ok = Boolean.class;
		};
	}

}
