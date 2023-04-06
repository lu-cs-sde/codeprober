package protocolgen.spec;

@SuppressWarnings("unused")
public class GetWorkerStatus extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "Concurrent:GetWorkerStatus";
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object stackTrace = arr(String.class);
		};
	}

}
