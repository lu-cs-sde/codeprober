package protocolgen.spec;

import codeprober.protocol.GetTestSuiteContentsErrorCode;

@SuppressWarnings("unused")
public class GetTestSuite extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "Test:GetTestSuite";
			public final Object suite = String.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object result = TestSuiteOrError.class;
		};
	}

	public static class TestSuiteOrError extends StreamableUnion {
		public final Object err = GetTestSuiteContentsErrorCode.class;
		public final Object contents = TestSuite.class;
	}
}
