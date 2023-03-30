package codeprober.locator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.locator.CreateLocator.LocatorMergeMethod;
import codeprober.metaprogramming.Reflect;
import codeprober.metaprogramming.TypeIdentificationStyle;
import codeprober.protocol.ParameterTypeDetail;

public class TestCreateLocator {

	private void assertSpan(Span expected, int actualStart, int actualEnd) {
		assertEquals(expected.start, actualStart);
		assertEquals(expected.end, actualEnd);
	}

	private JSONObject createLocator(AstInfo info, AstNode node) {
		CreateLocator.setMergeMethod(LocatorMergeMethod.PAPER_VERSION);
		return CreateLocator.fromNode(info, node);
	}

	@Test
	public void testSimpleRoot() {
		AstNode root = new AstNode(TestData.getSimple());
		final AstInfo info = TestData.getInfo(root);

		final JSONObject locator = createLocator(info, root);

		final JSONObject res = locator.getJSONObject("result");
		assertSpan(root.getRawSpan(info), res.getInt("start"), res.getInt("end"));
		assertEquals(TestData.Program.class.getName(), res.getString("type"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(0, steps.length());
	}

	@Test
	public void testSimpleFoo() {
		final AstNode root = new AstNode(TestData.getSimple());
		final AstInfo info = TestData.getInfo(root);
		final AstNode foo = root.getNthChild(info, 0);

		final JSONObject locator = createLocator(info, foo);

		final Consumer<JSONObject> assertFooLoc = (res) -> {
			assertSpan(foo.getRawSpan(info), res.getInt("start"), res.getInt("end"));
			assertEquals(TestData.Foo.class.getName(), res.getString("type"));
		};
		assertFooLoc.accept(locator.getJSONObject("result"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(1, steps.length());

		final JSONObject step = steps.getJSONObject(0);
		assertEquals("tal", step.getString("type"));
		assertFooLoc.accept(step.getJSONObject("value"));
	}

	@Test
	public void testSimpleBar() {
		final AstNode root = new AstNode(TestData.getSimple());
		final AstInfo info = TestData.getInfo(root);
		final AstNode bar = root.getNthChild(info, 0).getNthChild(info, 0);

		final JSONObject locator = createLocator(info, bar);

		final Consumer<JSONObject> assertBarLoc = (res) -> {
			assertSpan(bar.getRawSpan(info), res.getInt("start"), res.getInt("end"));
			assertEquals(TestData.Bar.class.getName(), res.getString("type"));
		};
		assertBarLoc.accept(locator.getJSONObject("result"));

		final JSONArray steps = locator.getJSONArray("steps");
		// Only 1 because the Program->Foo step is unnecessary.
		assertEquals(1, steps.length());

		final JSONObject step = steps.getJSONObject(0);
		assertEquals("tal", step.getString("type"));

		assertBarLoc.accept(step.getJSONObject("value"));
	}

	@Test
	public void testAmbiguousBar() {
		final AstNode root = new AstNode(TestData.getFlatAmbiguous());
		final AstInfo info = TestData.getInfo(root);
		final AstNode foo = root.getNthChild(info, 0);
		final AstNode bar = foo.getNthChild(info, 1);

		final JSONObject locator = createLocator(info, bar);

		final Consumer<JSONObject> assertBarLoc = (res) -> {
			assertSpan(bar.getRawSpan(info), res.getInt("start"), res.getInt("end"));
			assertEquals(TestData.Bar.class.getName(), res.getString("type"));
		};
		assertBarLoc.accept(locator.getJSONObject("result"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(2, steps.length());

		final JSONObject fooStep = steps.getJSONObject(0);
		assertEquals("In " + locator, "tal", fooStep.getString("type"));
		final JSONObject fooLocator = fooStep.getJSONObject("value");
		assertSpan(foo.getRawSpan(info), fooLocator.getInt("start"), fooLocator.getInt("end"));
		assertEquals(TestData.Foo.class.getName(), fooLocator.getString("type"));

		final JSONObject barStep = steps.getJSONObject(1);
		assertEquals("child", barStep.getString("type"));
		assertEquals(1, barStep.getInt("value"));
	}

	@Test
	public void testSimpleNta() {
		final AstNode root = new AstNode(TestData.getWithSimpleNta());
		final AstInfo info = TestData.getInfo(root);
		final AstNode foo = new AstNode(Reflect.invoke0(info.ast.underlyingAstNode, "simpleNTA"));

		final JSONObject locator = createLocator(info, foo);

		final JSONObject res = locator.getJSONObject("result");
		assertSpan(foo.getRecoveredSpan(info), res.getInt("start"), res.getInt("end"));
		assertEquals(TestData.Foo.class.getName(), res.getString("type"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(1, steps.length());

		final JSONObject ntaStep = steps.getJSONObject(0);
		assertEquals("nta", ntaStep.getString("type"));

		final JSONObject nta = ntaStep.getJSONObject("value");
		assertEquals("simpleNTA", nta.getString("name"));
		assertEquals(0, nta.getJSONArray("args").length());

	}

	@Test
	public void testParameterizedNtaFoo() {
		final AstNode root = new AstNode(TestData.getWithParameterizedNta());
		final AstInfo info = TestData.getInfo(root);
		final AstNode bar = new AstNode(Reflect.invokeN(info.ast.underlyingAstNode, "parameterizedNTA",
				new Class<?>[] { Integer.TYPE, info.baseAstClazz },
				new Object[] { 1, info.ast.getNthChild(info, 0).underlyingAstNode })).getNthChild(info, 1);

		final JSONObject locator = createLocator(info, bar);

		final JSONObject res = locator.getJSONObject("result");
		assertSpan(bar.getRecoveredSpan(info), res.getInt("start"), res.getInt("end"));
		assertEquals(TestData.Bar.class.getName(), res.getString("type"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(2, steps.length());

		final JSONObject ntaStep = steps.getJSONObject(0);
		assertEquals("nta", ntaStep.getString("type"));

		final JSONObject nta = ntaStep.getJSONObject("value");
		assertEquals("parameterizedNTA", nta.getString("name"));
		final JSONArray args = nta.getJSONArray("args");
		assertEquals(2, args.length());

		final JSONObject intArg = args.getJSONObject(0);
		assertEquals(ParameterTypeDetail.NORMAL.toString(), intArg.getString("detail"));
		assertEquals("int", intArg.getString("type"));
		assertEquals(1, intArg.getInt("value"));

		final JSONObject nodeArg = args.getJSONObject(1);
		assertEquals(ParameterTypeDetail.AST_NODE.toString(), nodeArg.getString("detail"));
		assertEquals(info.baseAstClazz.getName(), nodeArg.getString("type"));

		final JSONObject nodeLocator = nodeArg.getJSONObject("value");
		Consumer<JSONObject> assertFooLocator = (fooLoc) -> {
			assertSpan(info.ast.getNthChild(info, 0).getRecoveredSpan(info), fooLoc.getInt("start"),
					fooLoc.getInt("end"));
			assertEquals(TestData.Foo.class.getName(), fooLoc.getString("type"));
		};
		assertFooLocator.accept(nodeLocator.getJSONObject("result"));

		final JSONArray nodeLocatorSteps = nodeLocator.getJSONArray("steps");
		assertEquals(1, nodeLocatorSteps.length());

		final JSONObject fooStep = nodeLocatorSteps.getJSONObject(0);
		assertEquals("tal", fooStep.getString("type"));
		assertFooLocator.accept(fooStep.getJSONObject("value"));

		final JSONObject childStep = steps.getJSONObject(1);
		assertEquals("child", childStep.getString("type"));
		assertEquals(1, childStep.getInt("value"));
	}

	@Test
	public void testAmbiguousUncle() {
		AstNode root = new AstNode(TestData.getAmbiguousUncle());
		final AstInfo info = TestData.getInfo(root);
		final AstNode bar = root.getNthChild(info, 1).getNthChild(info, 0);

		final JSONObject locator = createLocator(info, bar);

		final JSONObject res = locator.getJSONObject("result");
		assertSpan(bar.getRawSpan(info), res.getInt("start"), res.getInt("end"));
		assertEquals(TestData.Bar.class.getName(), res.getString("type"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(1, steps.length());

		final JSONObject step = steps.getJSONObject(0);
		assertEquals("tal", step.getString("type"));

		final JSONObject tal = step.getJSONObject("value");
		assertSpan(bar.getRawSpan(info), tal.getInt("start"), tal.getInt("end"));
		assertEquals(TestData.Bar.class.getName(), tal.getString("type"));
	}

	@Test
	public void testAmbigHierarchyRequiringTwoSequentialTal() {
		AstNode root = new AstNode(TestData.getIdenticalBarsWithDifferentGrandParents());
		final AstInfo info = TestData.getInfo(root);
		final AstNode qux = root.getNthChild(info, 1);
		final AstNode bar = qux.getNthChild(info, 0).getNthChild(info, 0);

		final JSONObject locator = createLocator(info, bar);

		final JSONObject res = locator.getJSONObject("result");
		assertSpan(bar.getRawSpan(info), res.getInt("start"), res.getInt("end"));
		assertEquals(TestData.Bar.class.getName(), res.getString("type"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(2, steps.length());

		final JSONObject quxStep = steps.getJSONObject(0);
		assertEquals("tal", quxStep.getString("type"));

		final JSONObject quxTal = quxStep.getJSONObject("value");
		assertSpan(qux.getRawSpan(info), quxTal.getInt("start"), quxTal.getInt("end"));
		assertEquals(TestData.Qux.class.getName(), quxTal.getString("type"));


		final JSONObject barStep = steps.getJSONObject(1);
		assertEquals("tal", barStep.getString("type"));

		final JSONObject barTal = barStep.getJSONObject("value");
		assertSpan(bar.getRawSpan(info), barTal.getInt("start"), barTal.getInt("end"));
		assertEquals(TestData.Bar.class.getName(), barTal.getString("type"));
	}

	@Test
	public void testExternalFileFlag() {
		final AstNode root = new AstNode(TestData.getIdenticalBarsWithDifferentGrandParents());
		final AstInfo info = TestData.getInfo(root);
		final AstNode qux = root.getNthChild(info, 1);

		final JSONObject locator = createLocator(info, qux);

		assertEquals(true, locator.getJSONObject("result").getBoolean("external"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(1, steps.length());

		final JSONObject step = steps.getJSONObject(0);
		assertEquals("tal", step.getString("type"));

		final JSONObject tal = step.getJSONObject("value");
		assertSpan(qux.getRawSpan(info), tal.getInt("start"), tal.getInt("end"));
		assertEquals(TestData.Qux.class.getName(), tal.getString("type"));
		assertEquals(true, tal.getBoolean("external"));
	}

	@Test
	public void testCreateLabeled() {
		final AstNode root = new AstNode(TestData.getWithLabels());

		final BiFunction<JSONObject, String, JSONObject> assertLocator = (locator, expectedSingleStepType) -> {
			final JSONArray steps = locator.getJSONArray("steps");
			assertEquals(1, steps.length());

			final JSONObject step = steps.getJSONObject(0);
			assertEquals(expectedSingleStepType, step.getString("type"));

			return locator;
		};

		final BiConsumer<TypeIdentificationStyle, String> testIdentifications = (style, expectedLbl2Type) -> {
			final AstInfo info = TestData.getInfo(root, style);

			final AstNode lbl1 = root.getNthChild(info, 0);
			assertSame(lbl1, ApplyLocator.toNode(info, assertLocator.apply(createLocator(info, lbl1), "tal")).node);

			final AstNode lbl2 = root.getNthChild(info, 1);
			assertSame(lbl2, ApplyLocator.toNode(info, assertLocator.apply(createLocator(info, lbl2), expectedLbl2Type)).node);
		};

		testIdentifications.accept(TypeIdentificationStyle.REFLECTION, "child");
		testIdentifications.accept(TypeIdentificationStyle.NODE_LABEL, "tal");
	}
}
