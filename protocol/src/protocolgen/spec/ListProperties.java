package protocolgen.spec;

@SuppressWarnings("unused")
public class ListProperties extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "ListProperties";
			public final Object all = Boolean.class;
			public final Object locator = NodeLocator.class;
			public final Object src = ParsingRequestData.class;
			public final Object attrChain = opt(arr(String.class));
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object body = arr(RpcBodyLine.class);
			public final Object properties = opt(arr(Property.class));
		};
	}

}
