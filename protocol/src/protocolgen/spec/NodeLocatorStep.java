package protocolgen.spec;

public class NodeLocatorStep extends StreamableUnion {
	public final Object child = Integer.class;
	public final Object nta = FNStep.class;
	public final Object tal = TALStep.class;
}
