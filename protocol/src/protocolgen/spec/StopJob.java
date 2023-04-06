package protocolgen.spec;

@SuppressWarnings("unused")
public class StopJob extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "Concurrent:StopJob";
			public final Object job = Integer.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object err = opt(String.class);
		};
	}

}
