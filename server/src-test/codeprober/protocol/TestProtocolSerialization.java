package codeprober.protocol;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.ParsingSource;

public class TestProtocolSerialization {

	@Test
	public void testRoundTripSerialization() {
		final ParsingRequestData original = new ParsingRequestData(PositionRecoveryStrategy.ALTERNATE_PARENT_CHILD, AstCacheStrategy.FULL, ParsingSource.fromText("foo"), null, ".bar");
		final ParsingRequestData roundTrip = ParsingRequestData.fromJSON(original.toJSON());

		assertEquals(original.posRecovery, roundTrip.posRecovery);
		assertEquals(original.cache, roundTrip.cache);
		assertEquals("foo", roundTrip.src.asText());
		assertEquals(original.mainArgs, roundTrip.mainArgs);
		assertEquals(original.tmpSuffix, roundTrip.tmpSuffix);
	}
}
