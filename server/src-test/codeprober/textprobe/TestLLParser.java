package codeprober.textprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import codeprober.textprobe.ast.ASTList;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Probe;

public class TestLLParser {

	private Probe parseSingle(String src) {
		final ASTList<Container> containers = LLParser.parse(src, '[', ']').containers;
		assertEquals(1, containers.getNumChild());
		return containers.get(0).probe();

	}

	@Test
	public void testNoEqWithTilde() {
		assertNull(parseSingle("[[A.b!~true]]"));
	}

	@Test
	public void testNoEqWithExcl() {
		assertNull(parseSingle("[[A.b!true]]"));
	}

	@Test
	public void testNoEqWithTildeExcl() {
		assertNull(parseSingle("[[A.b!~true]]"));
	}

	@Test
	public void testLabeledProperty() {
		final Probe p = parseSingle("[[A.l:b=c]]");
		assertNotNull(p);
		assertEquals("A.l:b=c", p.pp());
	}
}
