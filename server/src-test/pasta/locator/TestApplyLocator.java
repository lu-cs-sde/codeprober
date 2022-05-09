package pasta.locator;

import java.util.function.Function;

import org.json.JSONObject;

import junit.framework.TestCase;
import pasta.AstInfo;
import pasta.ast.AstNode;
import pasta.ast.TestData;
import pasta.locator.ApplyLocator.ResolvedNode;

public class TestApplyLocator extends TestCase {

	private void assertMirror(Object ast, Function<AstNode, AstNode> pickTestNode) {
		final AstInfo info = TestData.getInfo(new AstNode(ast));
		final AstNode node = pickTestNode.apply(info.ast);

		final JSONObject locator = CreateLocator.fromNode(info, node);
		final ResolvedNode result = ApplyLocator.toNode(info, locator);

		assertNotNull(result);
		assertSame(node, result.node);
	}

	public void testMirrorSimpleProgram() {
		assertMirror(TestData.getSimple(), ast -> ast);
	}

	public void testMirrorSimpleFoo() {
		assertMirror(TestData.getSimple(), ast -> ast.getNthChild(0));
	}

	public void testMirrorSimpleBar() {
		assertMirror(TestData.getSimple(), ast -> ast.getNthChild(0).getNthChild(0));
	}

	public void testMirrorSimpleBaz() {
		assertMirror(TestData.getSimple(), ast -> ast.getNthChild(1));
	}

	public void testMirrorAmbiguousProgram() {
		assertMirror(TestData.getAmbiguous(), ast -> ast);
	}

	public void testMirrorAmbiguousFoo() {
		assertMirror(TestData.getAmbiguous(), ast -> ast.getNthChild(0));
	}

	public void testMirrorAmbiguousBar() {
		assertMirror(TestData.getAmbiguous(), ast -> ast.getNthChild(0).getNthChild(0));
		assertMirror(TestData.getAmbiguous(), ast -> ast.getNthChild(0).getNthChild(1));
	}
}
