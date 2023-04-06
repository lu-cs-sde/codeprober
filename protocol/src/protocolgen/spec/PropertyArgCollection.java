package protocolgen.spec;

public class PropertyArgCollection extends Streamable {
	public final Object type = String.class;
	public final Object entries = arr(PropertyArg.class);
}
