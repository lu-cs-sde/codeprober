package protocolgen.spec;

@SuppressWarnings("unused")
public class UnsubscribeFromWorkerStatus extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "Concurrent:UnsubscribeFromWorkerStatus";
			public final Object job = Integer.class;
			public final Object subscriberId = Integer.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object ok = Boolean.class;
		};
	}

}
