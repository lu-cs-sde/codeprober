package protocolgen.spec;

public class NestedTest extends Streamable {
	// Index chain in parent's output.
	public final Object path = arr(Integer.class);
	public final Object property = Property.class;
	public final Object expectedOutput = arr(RpcBodyLine.class);
	public final Object nestedProperties = arr(NestedTest.class);
}
