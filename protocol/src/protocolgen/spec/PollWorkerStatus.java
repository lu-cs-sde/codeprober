package protocolgen.spec;

@SuppressWarnings("unused")
public class PollWorkerStatus extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "Concurrent:PollWorkerStatus";
			public final Object job = Long.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object ok = Boolean.class;
		};
	}

}
