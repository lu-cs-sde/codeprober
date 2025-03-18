package protocolgen.spec;

public class WorkspacePathsUpdated extends Streamable {
	public final Object type = "workspace_paths_updated";
	public final Object paths = arr(String.class);
}
