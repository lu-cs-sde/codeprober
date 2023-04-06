package protocolgen.spec;

public class TestCaseAssert extends StreamableUnion {
	public final Object identity =  arr(RpcBodyLine.class);
	public final Object set =  arr(RpcBodyLine.class);
	public final Object smoke = Boolean.class;
}
