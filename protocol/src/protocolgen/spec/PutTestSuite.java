package protocolgen.spec;

import codeprober.protocol.PutTestSuiteContentsErrorCode;

@SuppressWarnings("unused")
public class PutTestSuite extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "Test:PutTestSuite";
			public final Object suite = String.class;
			public final Object contents = TestSuite.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object err = opt(PutTestSuiteContentsErrorCode.class);
		};
	}

}
