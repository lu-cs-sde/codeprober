package protocolgen.spec;

import java.util.Arrays;
import java.util.Optional;

public class Streamable {

	protected static Object opt(Object val) { return Optional.of(val); }
	protected static Object arr(Object val) { return Arrays.asList(val); }
	protected static Object oneOf(String... vals) { return vals; }

}
