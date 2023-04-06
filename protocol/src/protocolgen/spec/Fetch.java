package protocolgen.spec;

@SuppressWarnings("unused")
public class Fetch extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "Fetch";
			public final Object url = String.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object result = opt(String.class);
		};
	}

}
