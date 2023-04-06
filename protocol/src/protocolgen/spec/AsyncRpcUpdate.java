package protocolgen.spec;

public class AsyncRpcUpdate extends Streamable {
	public final Object type = "asyncUpdate";
	public final Object job = Long.class;
	public final Object isFinalUpdate = Boolean.class;
	public final Object value = AsyncRpcUpdateValue.class;
}
