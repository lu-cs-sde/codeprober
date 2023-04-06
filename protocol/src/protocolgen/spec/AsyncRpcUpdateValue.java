package protocolgen.spec;

public class AsyncRpcUpdateValue extends StreamableUnion {

	public final Object status = String.class;
	public final Object workerStackTrace = arr(String.class);
	public final Object workerStatuses = arr(String.class);
	public final Object workerTaskDone = WorkerTaskDone.class;
}
