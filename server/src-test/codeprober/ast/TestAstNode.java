package codeprober.ast;

import codeprober.AstInfo;
import codeprober.locator.Span;
import codeprober.metaprogramming.AstNodeApiStyle;
import junit.framework.TestCase;

public class TestAstNode extends TestCase {

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
	
}
