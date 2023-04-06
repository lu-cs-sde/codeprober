package protocolgen.spec;

import codeprober.protocol.ListTestSuitesErrorCode;

@SuppressWarnings("unused")
public class ListTestSuites extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "Test:ListTestSuites";
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object result = TestSuiteListOrError.class;
		};
	}

	public static class TestSuiteListOrError extends StreamableUnion {
		public final Object err = ListTestSuitesErrorCode.class;
		public final Object suites = arr(String.class);

	}
}
