package protocolgen.spec;

public class ListedTreeNode extends Streamable {
	public final Object type = "node";
	public final Object locator = NodeLocator.class;
	public final Object name = opt(String.class);
	public final Object children = ListedTreeChildNode.class;
}
