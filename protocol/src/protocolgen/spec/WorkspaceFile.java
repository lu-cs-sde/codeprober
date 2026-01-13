package protocolgen.spec;

public class WorkspaceFile extends Streamable {
	public final Object name = String.class;
	public final Object readOnly = opt(Boolean.class);
}
