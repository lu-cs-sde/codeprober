package protocolgen.spec;

@SuppressWarnings("unused")
public class Complete extends Rpc{

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "ide:complete";
			public final Object src = ParsingRequestData.class;
			public final Object line = Integer.class;
			public final Object column = Integer.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object lines = opt(arr(String.class));
		};
	}

}
