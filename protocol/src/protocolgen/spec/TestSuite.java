package protocolgen.spec;

public class TestSuite extends Streamable {
	// Version flag, used for upggrading files in the future
	public final Object v = Integer.class;
	public final Object cases = arr(TestCase.class);
}
