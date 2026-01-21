package codeprober.textprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Probe;
import codeprober.textprobe.ast.Query;
import codeprober.textprobe.ast.VarDecl;

public class TestTextProbeParser {

	private static class ParsedTextProbes {
		public final List<VarDecl> assignments = new ArrayList<>();
		public final List<Query> assertions = new ArrayList<>();
	}

	private ParsedTextProbes doParse(String src) {
		ParsedTextProbes ret = new ParsedTextProbes();
		for (Container c : Parser.parse(src, '[', ']').containers) {
			Probe p = c.probe();
			if (p == null) {
				continue;
			}
			switch (p.type) {
			case QUERY:
				ret.assertions.add(p.asQuery());
				break;
			case VARDECL:
				ret.assignments.add(p.asVarDecl());
				break;
			}
		}
		return ret;
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

		final VarDecl assign = ptp.assignments.get(0);
		assertEquals("$x:=y", assign.pp());
		assertEquals("x", assign.name.value);
		assertEquals("y", assign.src.pp());
	}

	@Test
	public void testAssert() {
		final ParsedTextProbes ptp = doParse("[[Foo.bar.baz=y]]");
		assertEquals(0, ptp.assignments.size());
		assertEquals(1, ptp.assertions.size());

		final Query tam = ptp.assertions.get(0);
		assertEquals("Foo.bar.baz=\"y\"", tam.pp());
		assertEquals("Foo", tam.head.pp());
		assertEquals(null, tam.index);
		assertEquals(2, tam.tail.getNumChild());
		assertEquals("bar", tam.tail.getChild(0).pp());
		assertEquals("baz", tam.tail.getChild(1).pp());
		assertFalse(tam.assertion.get().tilde);
		assertFalse(tam.assertion.get().exclamation);
		assertEquals("\"y\"", tam.assertion.get().expectedValue.pp());
	}

	@Test
	public void testAssertVar() {
		final ParsedTextProbes ptp = doParse("[[$x=y]]");
		assertEquals(0, ptp.assignments.size());
		assertEquals(1, ptp.assertions.size());

		final Query tam = ptp.assertions.get(0);
		assertEquals("$x=\"y\"", tam.pp());
		assertEquals("$x", tam.head.pp());
		assertEquals(null, tam.index);
		assertEquals(0, tam.tail.getNumChild());
		assertFalse(tam.assertion.get().tilde);
		assertFalse(tam.assertion.get().exclamation);
		assertEquals("\"y\"", tam.assertion.get().expectedValue.pp());
	}

	@Test
	public void testIndexedVar() {
		final ParsedTextProbes ptp = doParse("[[$x[123].y=z]]");
		assertEquals(0, ptp.assignments.size());
		assertEquals(1, ptp.assertions.size());

		final Query tam = ptp.assertions.get(0);
		assertEquals("$x[123].y=\"z\"", tam.pp());
		assertEquals("$x", tam.head.pp());
		assertEquals(Integer.valueOf(123), tam.index);
		assertEquals(1, tam.tail.getNumChild());
		assertEquals("y", tam.tail.getChild(0).pp());
		assertFalse(tam.assertion.get().tilde);
		assertFalse(tam.assertion.get().exclamation);
		assertEquals("\"z\"", tam.assertion.get().expectedValue.pp());
	}

	@Test
	public void testNestedBracketsBothOpenAndClose() {
		final ParsedTextProbes ptp = doParse("[[A.b=[[2]]]]");
		assertEquals(1, ptp.assertions.size());
		assertEquals(0, ptp.assignments.size());
		assertEquals("A.b=\"[[2]]\"", ptp.assertions.get(0).pp());
	}

	@Test
	public void testNestedBracketsOnlyOpen() {
		// Same as testNestedBracketsBothOpenAndClose, but no duplicated "]]".
		final ParsedTextProbes ptp = doParse("[[A.b=[[2]]");
		assertEquals(1, ptp.assertions.size());
		assertEquals(0, ptp.assignments.size());

		final Query tam = ptp.assertions.get(0);
		assertEquals("A.b=\"[[2\"", tam.pp());
	}

	@Test
	public void testMissingAssertion() {
		final ParsedTextProbes ptp = doParse("[[Foo]]");
		assertEquals(1, ptp.assertions.size());
		assertEquals(0, ptp.assignments.size());

		final Query tam = ptp.assertions.get(0);
		assertEquals("Foo", tam.head.pp());
		assertFalse(tam.assertion.isPresent());
	}
}
