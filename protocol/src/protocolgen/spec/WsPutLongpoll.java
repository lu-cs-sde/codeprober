package protocolgen.spec;

import org.json.JSONObject;

@SuppressWarnings("unused")
public class WsPutLongpoll extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "wsput:longpoll";
			public final Object session = String.class;
			public final Object etag = Integer.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object data = opt(LongPollResponse.class);
		};
	}

}
