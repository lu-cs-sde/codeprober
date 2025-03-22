package protocolgen.spec;

import org.json.JSONObject;

@SuppressWarnings("unused")
public class RenameWorkspacePath extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "RenameWorkspacePath";
			public final Object srcPath = String.class;
			public final Object dstPath = String.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object ok = Boolean.class;
		};
	}

}
