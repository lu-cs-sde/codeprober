package protocolgen.spec;

public class Property extends Streamable {
	public final Object name = String.class;
	public final Object args = opt(arr(PropertyArg.class));
	public final Object astChildName = opt(String.class);
}
