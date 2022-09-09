package codeprober.locator;

import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.locator.ApplyLocator.ResolvedNode;
import junit.framework.TestCase;

public class TestApplyLocator extends TestCase {

	private void assertMirror(Object ast, Function<AstInfo, AstNode> pickTestNode) {
		final AstInfo info = TestData.getInfo(new AstNode(ast));
		final AstNode node = pickTestNode.apply(info);

		final JSONObject locator = CreateLocator.fromNode(info, node);
		final ResolvedNode result = ApplyLocator.toNode(info, locator);

		assertNotNull(result);
		assertSame(node, result.node);
	}

	public void testMirrorSimpleProgram() {
		assertMirror(TestData.getSimple(), info -> info.ast);
	}

	public void testMirrorSimpleFoo() {
		assertMirror(TestData.getSimple(), info -> info.ast.getNthChild(info, 0));
	}

	public void testMirrorSimpleBar() {
		assertMirror(TestData.getSimple(), info -> info.ast.getNthChild(info, 0).getNthChild(info, 0));
	}

	public void testMirrorSimpleBaz() {
		assertMirror(TestData.getSimple(), info -> info.ast.getNthChild(info, 1));
	}

	public void testMirrorAmbiguousProgram() {
		assertMirror(TestData.getFlatAmbiguous(), info -> info.ast);
	}

	public void testMirrorAmbiguousFoo() {
		assertMirror(TestData.getFlatAmbiguous(), info -> info.ast.getNthChild(info, 0));
	}

	public void testMirrorHillyAmbiguousBar() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getHillyAmbiguous()));
		final AstNode shallowBar = info.ast.getNthChild(info, 0);
		final AstNode deepBar = info.ast.getNthChild(info, 1).getNthChild(info, 0);
		final JSONObject locator = new JSONObject().put("steps", new JSONArray().put(//
				new TypeAtLocEdge(info.ast, TypeAtLoc.from(info, info.ast), shallowBar, TypeAtLoc.from(info, shallowBar), 1).toJson()));

		final JSONObject talStep = locator.getJSONArray("steps").getJSONObject(0);
		assertEquals("tal", talStep.getString("type"));

		final JSONObject talPos = talStep.getJSONObject("value");
		assertEquals(1, talPos.getInt("depth"));
		assertSame(shallowBar, ApplyLocator.toNode(info, locator).node);
		// Increase depth
		// Start/end perfectly matches the shallowBar, but depth is now a bad match.
		// Start/end and depth both mismatch the deepBar, but depth matches "better"
		// than the shallow, so it should win.
		talPos.put("depth", 4);

		final ResolvedNode result = ApplyLocator.toNode(info, locator);

		assertNotNull(result);
		assertSame(deepBar, result.node);
	}

//	.add(new Bar(lc(1, 2), lc(1, 4))) //
	public void testMirrorAmbiguousBar() {
		assertMirror(TestData.getFlatAmbiguous(), info -> info.ast.getNthChild(info, 0).getNthChild(info, 0));
		assertMirror(TestData.getFlatAmbiguous(), info -> info.ast.getNthChild(info, 0).getNthChild(info, 1));
	}

	public void testSlightlyIncorrectLocator() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getSimple()));
		final AstNode foo = info.ast.getNthChild(info, 0);
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
