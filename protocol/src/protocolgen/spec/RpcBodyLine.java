package protocolgen.spec;

public class RpcBodyLine extends StreamableUnion {
	public final Object plain = String.class;
	public final Object stdout = String.class;
	public final Object stderr = String.class;
	public final Object streamArg = String.class;
	public final Object arr = arr(RpcBodyLine.class);
	public final Object node = NodeLocator.class;
	public final Object dotGraph = String.class;
	public final Object highlightMsg = HighlightableMessage.class;
}