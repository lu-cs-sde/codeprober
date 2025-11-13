package protocolgen.spec;

public class Tracing extends Streamable {

	public final Object node = NodeLocator.class;
	public final Object prop = Property.class;
	public final Object dependencies = arr(Tracing.class);
	public final Object result = RpcBodyLine.class;
	public final Object isCircular = opt(Boolean.class);
}
