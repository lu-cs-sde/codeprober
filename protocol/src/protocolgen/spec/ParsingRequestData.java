package protocolgen.spec;

import java.util.Arrays;
import java.util.Optional;

import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.PositionRecoveryStrategy;

public class ParsingRequestData extends Streamable {
	public final Object posRecovery = PositionRecoveryStrategy.class;
	public final Object cache = AstCacheStrategy.class;
	public final Object src = ParsingSource.class;
	public final Object mainArgs = Optional.of(Arrays.asList(String.class));
	public final Object tmpSuffix = String.class;
}
