package protocolgen.spec;

@SuppressWarnings("unused")
public class WsPutInit extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "wsput:init";
			public final Object session = String.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object info = InitInfo.class;
		};
	}

}
