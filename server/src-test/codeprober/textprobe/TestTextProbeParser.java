package codeprober.textprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TestTextProbeParser {

	private ParsedTextProbes doParse(String src) {
		return ParsedTextProbes.fromFileContents(src);
	}

	@Test
	public void testEmpty() {
		final ParsedTextProbes ptp = doParse("");
		assertEquals(0, ptp.assignments.size());
		assertEquals(0, ptp.assertions.size());
	}

	@Test
	public void testAssign() {
		final ParsedTextProbes ptp = doParse("[[$x:=y]]");
		assertEquals(1, ptp.assignments.size());
		assertEquals(0, ptp.assertions.size());

		final VarAssignMatch assign = ptp.assignments.get(0);
		assertEquals("$x:=y", assign.toString());
		assertEquals("$x", assign.varName);
		assertEquals("y", assign.srcVal);
	}

	@Test
	public void testAssert() {
		final ParsedTextProbes ptp = doParse("[[Foo.bar.baz=y]]");
		assertEquals(0, ptp.assignments.size());
		assertEquals(1, ptp.assertions.size());

		final TextAssertionMatch tam = ptp.assertions.get(0);
		assertEquals("Foo.bar.baz=y", tam.toString());
		assertEquals("Foo", tam.nodeType);
		assertEquals(null, tam.nodeIndex);
		assertEquals(2, tam.attrNames.length);
		assertEquals("bar", tam.attrNames[0]);
		assertEquals("baz", tam.attrNames[1]);
		assertFalse(tam.tilde);
		assertFalse(tam.exclamation);
		assertEquals("y", tam.expectVal);
	}

	@Test
	public void testAssertVar() {
		final ParsedTextProbes ptp = doParse("[[$x=y]]");
		assertEquals(0, ptp.assignments.size());
		assertEquals(1, ptp.assertions.size());

		final TextAssertionMatch tam = ptp.assertions.get(0);
		assertEquals("$x=y", tam.toString());
		assertEquals("$x", tam.nodeType);
		assertEquals(null, tam.nodeIndex);
		assertEquals(0, tam.attrNames.length);
		assertFalse(tam.tilde);
		assertFalse(tam.exclamation);
		assertEquals("y", tam.expectVal);
	}

	@Test
	public void testIndexedVar() {
		final ParsedTextProbes ptp = doParse("[[$x[123].y=z]]");
		assertEquals(0, ptp.assignments.size());
		assertEquals(1, ptp.assertions.size());

		final TextAssertionMatch tam = ptp.assertions.get(0);
		assertEquals("$x[123].y=z", tam.toString());
		assertEquals("$x", tam.nodeType);
		assertEquals(Integer.valueOf(123), tam.nodeIndex);
		assertEquals(1, tam.attrNames.length);
		assertEquals("y", tam.attrNames[0]);
		assertFalse(tam.tilde);
		assertFalse(tam.exclamation);
		assertEquals("z", tam.expectVal);
	}

	@Test
	public void testNestedBracketsBothOpenAndClose() {
		// Regression test of special case with nested "[[" and "]]"
		// Here, the expected value was "[[2]]". However, that inner "[[" caused the
		// outer text probe to be ignored.
		final ParsedTextProbes ptp = doParse("[[A.b=[[2]]]]");
		assertEquals(0, ptp.assertions.size());
		assertEquals(0, ptp.assignments.size());
	}

	@Test
	public void testNestedBracketsOnlyOpen() {
		// Same as testNestedBracketsBothOpenAndClose, but no duplicated "]]".
		// This makes the outer "A.b" part get ignored
		final ParsedTextProbes ptp = doParse("[[A.b=[[2]]");
		assertEquals(1, ptp.assertions.size());
		assertEquals(0, ptp.assignments.size());

		final TextAssertionMatch tam = ptp.assertions.get(0);
		assertEquals("2", tam.nodeType);
		assertNull(tam.expectVal);
	}

	@Test
	public void testMissingAssertion() {
		final ParsedTextProbes ptp = doParse("[[Foo]]");
		assertEquals(1, ptp.assertions.size());
		assertEquals(0, ptp.assignments.size());

		final TextAssertionMatch tam = ptp.assertions.get(0);
		assertEquals("Foo", tam.nodeType);
		assertNull(tam.expectVal);
	}
}
