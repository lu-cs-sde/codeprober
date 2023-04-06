package protocolgen.spec;

@SuppressWarnings("unused")
public class SubscribeToWorkerStatus extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "Concurrent:SubscribeToWorkerStatus";
			public final Object job = Integer.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object subscriberId = Integer.class;
		};
	}

}
