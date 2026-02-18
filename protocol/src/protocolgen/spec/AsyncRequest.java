package protocolgen.spec;

import org.json.JSONObject;

import protocolgen.spec.EvaluateProperty.PropertyEvaluationResult;
import protocolgen.spec.EvaluateProperty.SynchronousEvaluationResult;

@SuppressWarnings("unused")
public class AsyncRequest extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "AsyncRequest";
			public final Object src = JSONObject.class;
			public final Object job = opt(Long.class);
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object response = AsyncResult.class;
		};
	}

	public static class AsyncResult extends StreamableUnion {
		public final Object job = Long.class;
		public final Object sync = JSONObject.class;
	}
}
