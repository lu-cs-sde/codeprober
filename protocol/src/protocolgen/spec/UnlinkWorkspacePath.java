package protocolgen.spec;

import org.json.JSONObject;

@SuppressWarnings("unused")
public class UnlinkWorkspacePath extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "UnlinkWorkspacePath";
			public final Object path = String.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object ok = Boolean.class;
		};
	}

}
