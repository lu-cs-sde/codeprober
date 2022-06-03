package pasta.locator;

import java.util.function.Function;

import org.json.JSONObject;

import junit.framework.TestCase;
import pasta.AstInfo;
import pasta.ast.AstNode;
import pasta.ast.TestData;
import pasta.ast.TestData.Bar;
import pasta.ast.TestData.Baz;
import pasta.ast.TestData.Foo;
import pasta.ast.TestData.Program;
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
	
	public void testSlightlyIncorrectLocator() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getSimple()));
		final AstNode foo = info.ast.getNthChild(0);
		final JSONObject locator = CreateLocator.fromNode(info, foo);
		
		final JSONObject talStep = locator.getJSONArray("steps").getJSONObject(0);
		assertEquals("tal", talStep.getString("type"));

		final JSONObject talPos = talStep.getJSONObject("value");
		// Shift start/end to be 2 columns off the target.
		// ApplyLocator should permit minor errors.
		talPos.put("end", talPos.getInt("start") - 2);
		talPos.put("start", talPos.getInt("start") - 3);
		
		final ResolvedNode result = ApplyLocator.toNode(info, locator);

		assertNotNull(result);
		assertSame(foo, result.node);
	}
}
