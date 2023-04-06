package protocolgen.spec;

public class NullableNodeLocator extends Streamable {
	public final Object type = String.class;
	public final Object value = opt(NodeLocator.class);

}
