package protocolgen.spec;

public class TALStep extends Streamable{
	public final Object type = String.class;
	public final Object label = opt(String.class);
	public final Object start = Integer.class;
	public final Object end = Integer.class;
	public final Object depth = Integer.class;
	public final Object external = opt(Boolean.class);
}
