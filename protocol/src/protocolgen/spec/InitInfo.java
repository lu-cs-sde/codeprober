package protocolgen.spec;

@SuppressWarnings("unused")
public class InitInfo extends Streamable{

	public final Object type = "init";

	public final Object version = new Streamable() {
		public final Object hash = String.class;
		public final Object clean = Boolean.class;
		public final Object buildTimeSeconds = opt(Integer.class);
	};

	public final Object changeBufferTime = opt(Integer.class);
	public final Object workerProcessCount = opt(Integer.class);
	public final Object disableVersionCheckerByDefault = opt(Boolean.class);
	public final Object backingFile = opt(BackingFile.class);
	public final Object autoReloadOnDisconnect = opt(Boolean.class);
	public final Object supportsWorkspaceMetadata = opt(Boolean.class);
	public final Object autoAsyncTimeoutMs = opt(Integer.class);
}
