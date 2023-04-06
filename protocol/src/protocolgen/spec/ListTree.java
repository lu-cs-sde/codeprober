package protocolgen.spec;

@SuppressWarnings("unused")
public class ListTree extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = oneOf("ListTreeUpwards", "ListTreeDownwards");
			public final Object locator = NodeLocator.class;
			public final Object src = ParsingRequestData.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object body = arr(RpcBodyLine.class);
			public final Object locator = opt(NodeLocator.class);
			public final Object node = opt(ListedTreeNode.class);
		};
	}
}
