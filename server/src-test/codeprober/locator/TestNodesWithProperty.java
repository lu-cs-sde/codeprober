package codeprober.locator;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

public class TestNodesWithProperty {

	private void assertPredicates(String src, String... expected) {
		assertEquals(Arrays.asList(expected), NodesWithProperty.parsePredicates(src));
	}

	@Test
	public void testParseOneBooleanPredicate() {
		assertPredicates("foo", "foo");
	}

	@Test
	public void testParseOneStringPredicate() {
		assertPredicates("foo=bar", "foo=bar");
	}

	@Test
	public void testParseTwoBooleanPredicates() {
		assertPredicates("foo&bar", "foo", "bar");
	}

	@Test
	public void testParseTwoStringPredicates() {
		assertPredicates("foo=bar&lorem=ipsum", "foo=bar", "lorem=ipsum");
	}

	@Test
	public void testParseMultipleMixedPredicatesWithWhitespaces() {
		assertPredicates("foo&bar=baz &lorem=ipsum      & dolor", "foo", "bar=baz", "lorem=ipsum", "dolor");
	}

	@Test
	public void testParseRawNewline() {
		assertPredicates("foo=b\nr&baz", "foo=b\nr", "baz");
	}

	@Test
	public void testParseBackslashNewline() {
		assertPredicates("foo=b\\nr&baz", "foo=b\nr", "baz");
	}

	@Test
	public void testParseEscapedNewline() {
		assertPredicates("foo=b\\\\nr&baz", "foo=b\\nr", "baz");
	}

	@Test
	public void testPredicatesWithEscapedAmpersand() {
		assertPredicates("foo=b\\&r&baz", "foo=b&r", "baz");
	}

	@Test
	public void testPredicatesWithEscapedEscapeCharacters() {
		assertPredicates("foo=b\\\\&r&baz", "foo=b\\", "r", "baz");
	}

	@Test
	public void testPredicatesWithEscapedEscapedEscapeCharacters() {
		assertPredicates("foo=b\\\\\\&r&baz", "foo=b\\&r", "baz");
	}
}
