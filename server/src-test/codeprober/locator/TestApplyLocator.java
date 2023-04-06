package codeprober.locator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.NodeLocatorStep;
import codeprober.protocol.data.TALStep;

public class TestApplyLocator {

	private void assertMirror(Object ast, Function<AstInfo, AstNode> pickTestNode) {
		final AstInfo info = TestData.getInfo(new AstNode(ast));
		final AstNode node = pickTestNode.apply(info);

		final NodeLocator locator = CreateLocator.fromNode(info, node);
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
			BiFunction<TALStep, AstNode, TALStep> adjuster) {
		final AstNode shallowBar = info.ast.getNthChild(info, 0);
		final AstNode deepBar = info.ast.getNthChild(info, 1).getNthChild(info, 0);
		final TALStep talStep = CreateLocator.createTALStep(info, shallowBar, 1);
//		final JSONObject locator = new JSONObject().put("steps", new JSONArray().put(//
//				new TypeAtLocEdge(info.ast, TypeAtLoc.from(info, info.ast), shallowBar,
//						TypeAtLoc.from(info, shallowBar), 1, false).toJson()));
		final List<NodeLocatorStep> steps = Arrays.asList(NodeLocatorStep.fromTal(talStep));
		final TALStep dummyResult = new TALStep("", "", 0, 0, 0, null);

//		final JSONObject talStep = locator.getJSONArray("steps").getJSONObject(0);
//		assertEquals("tal", talStep.getString("type"));

//		final JSONObject talPos = talStep.getJSONObject("value");
//		assertEquals(1, talPos.getInt("depth"));
		assertEquals(shallowBar.getRecoveredSpan(info).start, talStep.start);
		assertEquals(shallowBar.getRecoveredSpan(info).end, talStep.end);
		assertSame(shallowBar, ApplyLocator.toNode(info, new NodeLocator(dummyResult, steps)).node);

		final TALStep adjusted = adjuster.apply(talStep, deepBar);

		final ResolvedNode result = ApplyLocator.toNode(info, new NodeLocator(dummyResult, Arrays.asList(NodeLocatorStep.fromTal(adjusted))));
		assertNotNull(result);
		assertSame(expect == ExpectedShiftedMatch.SHALLOW ? shallowBar : deepBar, result.node);
	}

	@Test
	public void testMatchAmbiguousBarWithShiftedDepth() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getHillyAmbiguous()));
		testMatchAmbiguousBarWithShifter(info, ExpectedShiftedMatch.SHALLOW, (tal, deepBar) -> {
			// Increase depth so it no longer matches the shallow bar.
			// Since start/end still matches perfectly, we should still match shallow in the
			// end.
			return new TALStep(tal.type, tal.label, tal.start, tal.end, 4, tal.external);
		});
	}

	@Test
	public void testMatchAmbiguousBarWithShiftedStartEnd() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getHillyAmbiguous()));
		testMatchAmbiguousBarWithShifter(info, ExpectedShiftedMatch.DEEP, (tal, deepBar) -> {
			// Change start/end to perfectly match deepBar
			// The depth value is wrong, but perfect start/end match should take precedence.
			final Span deep = deepBar.getRecoveredSpan(info);
			return new TALStep(tal.type, tal.label, deep.start, deep.end, tal.depth, tal.external);
		});
	}

	@Test
	public void testMatchAmbiguousBarWithShiftedStartEndSlightlyOff() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getHillyAmbiguous()));
		testMatchAmbiguousBarWithShifter(info, ExpectedShiftedMatch.SHALLOW, (tal, deepBar) -> {
			// Change start/end to _almost_ match deepBar
			// Non-perfect matches matter less than depth, so we should match shallow
			final Span deep = deepBar.getRecoveredSpan(info);
			return new TALStep(tal.type, tal.label, deep.start, deep.end + 1, tal.depth, tal.external);
		});
	}

	@Test
	public void testMatchAmbiguousBarWithShiftedStartDepth() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getHillyAmbiguous()));
		testMatchAmbiguousBarWithShifter(info, ExpectedShiftedMatch.DEEP, (tal, deepBar) -> {
			// Change start to no longer match shallowBar.
			// Also change depth to better match deepBar
			return new TALStep(tal.type, tal.label, tal.start - 1, tal.end, 3, tal.external);
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
		final NodeLocator locator = CreateLocator.fromNode(info, foo);

		final NodeLocatorStep talStep = locator.steps.get(0);
		assertEquals(NodeLocatorStep.Type.tal, talStep.type);

		final TALStep tal = talStep.asTal();
		// Shift start/end to be 2 columns off the target.
		// ApplyLocator should permit minor errors.
		final TALStep adjusted = new TALStep(tal.type, tal.label, tal.start - 3, tal.start - 2, tal.depth, tal.external);

		final ResolvedNode result = ApplyLocator.toNode(info, new NodeLocator(locator.result, Arrays.asList(NodeLocatorStep.fromTal(adjusted))));

		assertNotNull(result);
		assertSame(foo, result.node);
	}
}
