package protocolgen.spec;

public class NodeContainer extends Streamable {
	public final Object node = NodeLocator.class;
	public final Object body = opt(RpcBodyLine.class);
}