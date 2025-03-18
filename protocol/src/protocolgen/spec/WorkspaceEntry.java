package protocolgen.spec;

public class WorkspaceEntry extends StreamableUnion {
	public final Object file = String.class;
	public final Object directory = String.class;
}