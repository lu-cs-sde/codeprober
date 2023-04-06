package protocolgen.spec;

@SuppressWarnings("unused")
public class EvaluateProperty extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "EvaluateProperty";
			public final Object src = ParsingRequestData.class;
			public final Object locator = NodeLocator.class;
			public final Object property = Property.class;
			public final Object captureStdout = Boolean.class;
			public final Object job = opt(Long.class);
			public final Object jobLabel = opt(String.class);
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object response = PropertyEvaluationResult.class;
		};
	}

	public static class PropertyEvaluationResult extends StreamableUnion {
		public final Object job = Long.class;
		public final Object sync = SynchronousEvaluationResult.class;
	}

	public static class SynchronousEvaluationResult extends Streamable {
		public final Object body = arr(RpcBodyLine.class);
		public final Object totalTime = Long.class;
		public final Object parseTime = Long.class;
		public final Object createLocatorTime = Long.class;
		public final Object applyLocatorTime = Long.class;
		public final Object attrEvalTime = Long.class;
		public final Object listNodesTime = Long.class;
		public final Object listPropertiesTime = Long.class;
		public final Object errors = opt(arr(Diagnostic.class));
		public final Object args = opt(arr(PropertyArg.class));
		public final Object locator = opt(NodeLocator.class);
	}
}
