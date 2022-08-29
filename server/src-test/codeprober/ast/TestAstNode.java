package codeprober.ast;

import codeprober.ast.AstNode;
import codeprober.locator.Span;
import codeprober.metaprogramming.PositionRepresentation;
import junit.framework.TestCase;

public class TestAstNode extends TestCase {

	public void testPositionAndChildAccessors() {
		final AstNode an = new AstNode(TestData.getSimple());

		final Span span = an.getRawSpan(PositionRepresentation.PACKED_BITS);
		assertEquals(0, span.getStartLine());
		assertEquals(5, span.getEndLine());

		assertEquals(2, an.getNumChildren());

		final AstNode fooChild = an.getNthChild(0);
		final Span fooPos = fooChild.getRawSpan(PositionRepresentation.PACKED_BITS);
		assertEquals(1, fooPos.getStartLine());
		assertEquals(3, fooPos.getEndLine());
		assertEquals(1, fooChild.getNumChildren());
		
		int numIterator = 0;
		for (@SuppressWarnings("unused") AstNode child : an.getChildren()) {
			++numIterator;
		}
		assertEquals(2, numIterator);
	}
	
}
