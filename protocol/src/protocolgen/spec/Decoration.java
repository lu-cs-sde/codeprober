package protocolgen.spec;

public class Decoration extends Streamable {
	public Object start = Integer.class;
	public Object end = Integer.class;
	public Object type = String.class;
	public Object message = opt(String.class);
}
