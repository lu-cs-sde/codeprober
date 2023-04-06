package protocolgen.spec;

public class ListedTreeChildNode extends StreamableUnion {
	public final Object children = arr(ListedTreeNode.class);
	public final Object placeholder = Integer.class;
}