package codeprober.locator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.ast.TestData.Bar;

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

	@Test
	public void testSubtypeSearchCanFindRoot() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getSimple()));
		final List<Object> result = NodesWithProperty.get(info, info.ast, "", "this <: Program", 32);
		assertEquals(2, result.size());
		assertEquals("Found 1 node", result.get(0));
		assertEquals(info.ast, (result.get(1)));
	}

	@Test
	public void testSubtypeSearch() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getIdenticalBarsWithDifferentParents()));
		final List<Object> result = NodesWithProperty.get(info, info.ast, "",
				String.format("this<:%s", Bar.class.getName()), 32);
		assertEquals(3, result.size());
		assertEquals("Found 2 nodes", result.get(0));
		assertTrue(result.get(1) instanceof AstNode);
		assertTrue(result.get(2) instanceof AstNode);
	}

	@Test
	public void testSubtypeSearchWithSimpleClassName() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getIdenticalBarsWithDifferentParents()));
		final List<Object> result = NodesWithProperty.get(info, info.ast, "",
				String.format("this<:%s", Bar.class.getSimpleName()), 32);
		assertEquals(3, result.size());
		assertEquals("Found 2 nodes", result.get(0));
		assertTrue(result.get(1) instanceof AstNode);
		assertTrue(result.get(2) instanceof AstNode);
	}

	@Test
	public void testLabeledPropertySearch() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getSimple()));
		final List<Object> result = NodesWithProperty.get(info, info.ast, "",
				String.format("l:abc=%d", "Bar:abc".hashCode()), 32);
		assertEquals(2, result.size());
		assertEquals("Found 1 node", result.get(0));
	}
}
