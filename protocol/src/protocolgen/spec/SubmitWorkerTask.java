package protocolgen.spec;

import org.json.JSONObject;

@SuppressWarnings("unused")
public class SubmitWorkerTask extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "Concurrent:SubmitTask";
			public final Object job  = Long.class;
			public final Object data  = JSONObject.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object ok = Boolean.class;
		};
	}

}
