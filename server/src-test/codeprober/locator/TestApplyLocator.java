package codeprober.locator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.locator.ApplyLocator.ResolvedNode;

public class TestApplyLocator {

	private void assertMirror(Object ast, Function<AstInfo, AstNode> pickTestNode) {
		final AstInfo info = TestData.getInfo(new AstNode(ast));
		final AstNode node = pickTestNode.apply(info);

		final JSONObject locator = CreateLocator.fromNode(info, node);
		final ResolvedNode result = ApplyLocator.toNode(info, locator);

		assertNotNull(result);
		assertSame(node, result.node);
	}

	@Test
	public void testMirrorSimpleProgram() {
		assertMirror(TestData.getSimple(), info -> info.ast);
	}

	@Test
	public void testMirrorSimpleFoo() {
		assertMirror(TestData.getSimple(), info -> info.ast.getNthChild(info, 0));
	}

	@Test
	public void testMirrorSimpleBar() {
		assertMirror(TestData.getSimple(), info -> info.ast.getNthChild(info, 0).getNthChild(info, 0));
	}

	@Test
	public void testMirrorSimpleBaz() {
		assertMirror(TestData.getSimple(), info -> info.ast.getNthChild(info, 1));
	}

	@Test
	public void testMirrorAmbiguousProgram() {
		assertMirror(TestData.getFlatAmbiguous(), info -> info.ast);
	}

	@Test
	public void testMirrorAmbiguousFoo() {
		assertMirror(TestData.getFlatAmbiguous(), info -> info.ast.getNthChild(info, 0));
	}

	private enum ExpectedShiftedMatch {
		SHALLOW, DEEP
	}

	private void testMatchAmbiguousBarWithShifter(AstInfo info, ExpectedShiftedMatch expect,
			BiConsumer<JSONObject, AstNode> adjuster) {
		final AstNode shallowBar = info.ast.getNthChild(info, 0);
		final AstNode deepBar = info.ast.getNthChild(info, 1).getNthChild(info, 0);
		final JSONObject locator = new JSONObject().put("steps", new JSONArray().put(//
				new TypeAtLocEdge(info.ast, TypeAtLoc.from(info, info.ast), shallowBar,
						TypeAtLoc.from(info, shallowBar), 1, false).toJson()));

		final JSONObject talStep = locator.getJSONArray("steps").getJSONObject(0);
		assertEquals("tal", talStep.getString("type"));

		final JSONObject talPos = talStep.getJSONObject("value");
		assertEquals(1, talPos.getInt("depth"));
		assertEquals(shallowBar.getRecoveredSpan(info).start, talPos.getInt("start"));
		assertEquals(shallowBar.getRecoveredSpan(info).end, talPos.getInt("end"));
		assertSame(shallowBar, ApplyLocator.toNode(info, locator).node);

		adjuster.accept(talPos, deepBar);

		final ResolvedNode result = ApplyLocator.toNode(info, locator);
		assertNotNull(result);
		assertSame(expect == ExpectedShiftedMatch.SHALLOW ? shallowBar : deepBar, result.node);
	}

	@Test
	public void testMatchAmbiguousBarWithShiftedDepth() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getHillyAmbiguous()));
		testMatchAmbiguousBarWithShifter(info, ExpectedShiftedMatch.SHALLOW, (talPos, deepBar) -> {
			// Increase depth so it no longer matches the shallow bar.
			// Since start/end still matches perfectly, we should still match shallow in the
			// end.
			talPos.put("depth", 4);
		});
	}

	@Test
	public void testMatchAmbiguousBarWithShiftedStartEnd() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getHillyAmbiguous()));
		testMatchAmbiguousBarWithShifter(info, ExpectedShiftedMatch.DEEP, (talPos, deepBar) -> {
			// Change start/end to perfectly match deepBar
			// The depth value is wrong, but perfect start/end match should take precedence.
			talPos.put("start", deepBar.getRecoveredSpan(info).start);
			talPos.put("end", deepBar.getRecoveredSpan(info).end);
		});
	}

	@Test
	public void testMatchAmbiguousBarWithShiftedStartEndSlightlyOff() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getHillyAmbiguous()));
		testMatchAmbiguousBarWithShifter(info, ExpectedShiftedMatch.SHALLOW, (talPos, deepBar) -> {
			// Change start/end to _almost_ match deepBar
			// Non-perfect matches matter less than depth, so we should match shallow
			talPos.put("start", deepBar.getRecoveredSpan(info).start);
			talPos.put("end", deepBar.getRecoveredSpan(info).end + 1);
		});
	}

	@Test
	public void testMatchAmbiguousBarWithShiftedStartDepth() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getHillyAmbiguous()));
		testMatchAmbiguousBarWithShifter(info, ExpectedShiftedMatch.DEEP, (talPos, deepBar) -> {
			// Change start to no longer match shallowBar.
			talPos.put("start", talPos.getInt("start") - 1);
			// Also change depth to better match deepBar
			talPos.put("depth", 3);
		});
	}

	@Test
	public void testMirrorAmbiguousBar() {
		assertMirror(TestData.getFlatAmbiguous(), info -> info.ast.getNthChild(info, 0).getNthChild(info, 0));
		assertMirror(TestData.getFlatAmbiguous(), info -> info.ast.getNthChild(info, 0).getNthChild(info, 1));
	}

	@Test
	public void testMirrorIdenticalBarsWithDifferentParents() {
		assertMirror(TestData.getIdenticalBarsWithDifferentParents(),
				info -> info.ast.getNthChild(info, 0).getNthChild(info, 0));
		assertMirror(TestData.getIdenticalBarsWithDifferentParents(),
				info -> info.ast.getNthChild(info, 1).getNthChild(info, 0));
	}

	@Test
	public void testMirrorIdenticalBarsWithDifferentGrandParents() {
		// TODO turn this into more controlled test cases. This is where
		// "shortHop"/"startNewTAL" is needed
		assertMirror(TestData.getIdenticalBarsWithDifferentGrandParents(),
				info -> info.ast.getNthChild(info, 0).getNthChild(info, 0).getNthChild(info, 0));
		assertMirror(TestData.getIdenticalBarsWithDifferentGrandParents(),
				info -> info.ast.getNthChild(info, 1).getNthChild(info, 0).getNthChild(info, 0));
	}

	@Test
	public void testMirrorMultipleAmbiguousLevels() {
		assertMirror(TestData.getMultipleAmbiguousLevels(), info -> info.ast //
				.getNthChild(info, 0) // Foo
				.getNthChild(info, 0) // Bar
				.getNthChild(info, 0)); // Baz
	}

	@Test
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
