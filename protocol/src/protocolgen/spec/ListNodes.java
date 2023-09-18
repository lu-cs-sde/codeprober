package protocolgen.spec;

@SuppressWarnings("unused")
public class ListNodes extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "ListNodes";
			public final Object pos = Integer.class;
			public final Object src = ParsingRequestData.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object body = arr(RpcBodyLine.class);
			public final Object nodes = opt(arr(NodeLocator.class));
			public final Object errors = opt(arr(Diagnostic.class));
		};
	}

}
