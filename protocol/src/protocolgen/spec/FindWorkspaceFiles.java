package protocolgen.spec;

import org.json.JSONObject;

@SuppressWarnings("unused")
public class FindWorkspaceFiles extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "FindWorkspaceFiles";
			public final Object query = String.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object matches = opt(arr(String.class));
			public final Object truncatedSearch = opt(Boolean.class);
		};
	}

}
