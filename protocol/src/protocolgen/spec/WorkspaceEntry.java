package protocolgen.spec;

public class WorkspaceEntry extends StreamableUnion {
	public final Object file = WorkspaceFile.class;
	public final Object directory = String.class;
}