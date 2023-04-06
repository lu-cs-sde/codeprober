package protocolgen.spec;

import codeprober.protocol.TestCaseAssertType;

public class TestCase extends Streamable {
	public final Object name = String.class;
	public final Object src = ParsingRequestData.class;
	public final Object property = Property.class;
	public final Object locator = NodeLocator.class;
	public final Object assertType = TestCaseAssertType.class;
	public final Object expectedOutput = arr(RpcBodyLine.class);
	public final Object nestedProperties = arr(NestedTest.class);
}
