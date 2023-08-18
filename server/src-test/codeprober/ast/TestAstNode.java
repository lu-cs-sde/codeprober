package codeprober.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import codeprober.AstInfo;
import codeprober.locator.Span;
import codeprober.metaprogramming.AstNodeApiStyle;

public class TestAstNode {

	@Test
	public void testPositionAndChildAccessors() {
		final AstNode an = new AstNode(TestData.getSimple());
		final AstInfo info = TestData.getInfo(an);

		final Span span = an.getRawSpan(AstNodeApiStyle.BEAVER_PACKED_BITS);
		assertEquals(0, span.getStartLine());
		assertEquals(5, span.getEndLine());

		assertEquals(2, an.getNumChildren(info));

		final AstNode fooChild = an.getNthChild(info, 0);
		final Span fooPos = fooChild.getRawSpan(AstNodeApiStyle.BEAVER_PACKED_BITS);
		assertEquals(1, fooPos.getStartLine());
		assertEquals(3, fooPos.getEndLine());
		assertEquals(1, fooChild.getNumChildren(info));

		int numIterator = 0;
		for (@SuppressWarnings("unused") AstNode child : an.getChildren(info)) {
			++numIterator;
		}
		assertEquals(2, numIterator);
	}

	@Test
	public void testHasPropertyWorksWithoutPropertyListShow() {
		final AstNode an = new AstNode(TestData.getSimple());
		final AstInfo info = TestData.getInfo(an);
		assertTrue(an.hasProperty(info, "getNumChild"));
		assertFalse(an.hasProperty(info, "foobar"));
	}

	@Test
	public void testHasPropertyWorksWithPropertyListShow() {
		final AstNode an = new AstNode(new PropertyListShower());
		final AstInfo info = TestData.getInfo(an);
		assertFalse(an.hasProperty(info, "getNumChild"));
		assertTrue(an.hasProperty(info, "foobar"));
	}

	static class PropertyListShower {
		public List<String> cpr_propertyListShow() {
			return Arrays.asList("foobar");
		}
	}
}
