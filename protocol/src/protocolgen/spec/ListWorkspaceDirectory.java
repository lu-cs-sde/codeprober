package protocolgen.spec;

@SuppressWarnings("unused")
public class ListWorkspaceDirectory extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "ListWorkspaceDirectory";
			public final Object path = opt(String.class);
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object entries = opt(arr(WorkspaceEntry.class));
		};
	}

}
