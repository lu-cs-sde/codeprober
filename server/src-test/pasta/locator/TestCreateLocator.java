package pasta.locator;

import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

import junit.framework.TestCase;
import pasta.AstInfo;
import pasta.ast.AstNode;
import pasta.ast.TestData;
import pasta.metaprogramming.Reflect;

public class TestCreateLocator extends TestCase {

	private void assertSpan(Span expected, int actualStart, int actualEnd) {
		assertEquals(expected.start, actualStart);
		assertEquals(expected.end, actualEnd);
	}

	public void testSimpleRoot() {
		AstNode root = new AstNode(TestData.getSimple());
		final AstInfo info = TestData.getInfo(root);

		final JSONObject locator = CreateLocator.fromNode(info, root);

		final JSONObject res = locator.getJSONObject("result");
		assertSpan(root.getRawSpan(info), res.getInt("start"), res.getInt("end"));
		assertEquals("Program", res.getString("type"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(0, steps.length());
	}

	public void testSimpleFoo() {
		final AstNode root = new AstNode(TestData.getSimple());
		final AstInfo info = TestData.getInfo(root);
		final AstNode foo = root.getNthChild(0);

		final JSONObject locator = CreateLocator.fromNode(info, foo);

		final Consumer<JSONObject> assertFooLoc = (res) -> {
			assertSpan(foo.getRawSpan(info), res.getInt("start"), res.getInt("end"));
			assertEquals("Foo", res.getString("type"));
		};
		assertFooLoc.accept(locator.getJSONObject("result"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(1, steps.length());

		final JSONObject step = steps.getJSONObject(0);
		assertEquals("tal", step.getString("type"));
		assertFooLoc.accept(step.getJSONObject("value"));
//		assertEquals(0, step.getInt("value"));
	}

	public void testSimpleBar() {
		final AstNode root = new AstNode(TestData.getSimple());
		final AstInfo info = TestData.getInfo(root);
		final AstNode bar = root.getNthChild(0).getNthChild(0);

		final JSONObject locator = CreateLocator.fromNode(info, bar);

		final Consumer<JSONObject> assertBarLoc = (res) -> {
			assertSpan(bar.getRawSpan(info), res.getInt("start"), res.getInt("end"));
			assertEquals("Bar", res.getString("type"));
		};
		assertBarLoc.accept(locator.getJSONObject("result"));

		final JSONArray steps = locator.getJSONArray("steps");
		// Only 1 because the Program->Foo step is unnecessary.
		assertEquals(1, steps.length());

		final JSONObject step = steps.getJSONObject(0);
		assertEquals("tal", step.getString("type"));

		assertBarLoc.accept(step.getJSONObject("value"));
	}

	public void testAmbiguousBar() {
		final AstNode root = new AstNode(TestData.getAmbiguous());
		final AstInfo info = TestData.getInfo(root);
		final AstNode foo = root.getNthChild(0);
		final AstNode bar = foo.getNthChild(1);

		final JSONObject locator = CreateLocator.fromNode(info, bar);

		final Consumer<JSONObject> assertBarLoc = (res) -> {
			assertSpan(bar.getRawSpan(info), res.getInt("start"), res.getInt("end"));
			assertEquals("Bar", res.getString("type"));
		};
		assertBarLoc.accept(locator.getJSONObject("result"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(2, steps.length());

		final JSONObject fooStep = steps.getJSONObject(0);
		assertEquals("tal", fooStep.getString("type"));
		final JSONObject fooLocator = fooStep.getJSONObject("value");
		assertSpan(foo.getRawSpan(info), fooLocator.getInt("start"), fooLocator.getInt("end"));
		assertEquals("Foo", fooLocator.getString("type"));

		final JSONObject barStep = steps.getJSONObject(1);
		assertEquals("child", barStep.getString("type"));
		assertEquals(1, barStep.getInt("value"));
	}

	public void testSimpleNta() {
		final AstNode root = new AstNode(TestData.getWithSimpleNta());
		final AstInfo info = TestData.getInfo(root);
		final AstNode foo = new AstNode(Reflect.invoke0(info.ast.underlyingAstNode, "simpleNTA"));

		final JSONObject locator = CreateLocator.fromNode(info, foo);

		final JSONObject res = locator.getJSONObject("result");
		assertSpan(foo.getRecoveredSpan(info), res.getInt("start"), res.getInt("end"));
		assertEquals("Foo", res.getString("type"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(1, steps.length());

		final JSONObject ntaStep = steps.getJSONObject(0);
		assertEquals("nta", ntaStep.getString("type"));

		final JSONObject nta = ntaStep.getJSONObject("value");
		assertEquals("simpleNTA", nta.getString("name"));
		assertEquals(0, nta.getJSONArray("args").length());

	}

	public void testParameterizedNtaFoo() {
		final AstNode root = new AstNode(TestData.getWithParameterizedNta());
		final AstInfo info = TestData.getInfo(root);
		final AstNode bar = new AstNode(Reflect.invokeN(info.ast.underlyingAstNode, "parameterizedNTA",
				new Class<?>[] { Integer.TYPE, info.basAstClazz },
				new Object[] { 1, info.ast.getNthChild(0).underlyingAstNode })).getNthChild(1);

		final JSONObject locator = CreateLocator.fromNode(info, bar);

		final JSONObject res = locator.getJSONObject("result");
		assertSpan(bar.getRecoveredSpan(info), res.getInt("start"), res.getInt("end"));
		assertEquals("Bar", res.getString("type"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(2, steps.length());

		final JSONObject ntaStep = steps.getJSONObject(0);
		assertEquals("nta", ntaStep.getString("type"));

		final JSONObject nta = ntaStep.getJSONObject("value");
		assertEquals("parameterizedNTA", nta.getString("name"));
		final JSONArray args = nta.getJSONArray("args");
		assertEquals(2, args.length());

		final JSONObject intArg = args.getJSONObject(0);
		assertEquals(false, intArg.getBoolean("isNodeType"));
		assertEquals("int", intArg.getString("type"));
		assertEquals(1, intArg.getInt("value"));

		final JSONObject nodeArg = args.getJSONObject(1);
		assertEquals(true, nodeArg.getBoolean("isNodeType"));
		assertEquals(info.basAstClazz.getName(), nodeArg.getString("type"));

		final JSONObject nodeLocator = nodeArg.getJSONObject("value");
		Consumer<JSONObject> assertFooLocator = (fooLoc) -> {
			assertSpan(info.ast.getNthChild(0).getRecoveredSpan(info), fooLoc.getInt("start"),
					fooLoc.getInt("end"));
			assertEquals("Foo", fooLoc.getString("type"));
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
	
	public void testAmbiguousUncle() {
		AstNode root = new AstNode(TestData.getAmbiguousUncle());
		final AstInfo info = TestData.getInfo(root);
		final AstNode bar = root.getNthChild(1).getNthChild(0);

		final JSONObject locator = CreateLocator.fromNode(info, bar);

		final JSONObject res = locator.getJSONObject("result");
		assertSpan(bar.getRawSpan(info), res.getInt("start"), res.getInt("end"));
		assertEquals("Bar", res.getString("type"));

		final JSONArray steps = locator.getJSONArray("steps");
		assertEquals(1, steps.length());
		
		final JSONObject step = steps.getJSONObject(0);
		assertEquals("tal", step.getString("type"));
		
		final JSONObject tal = step.getJSONObject("value");
		assertSpan(bar.getRawSpan(info), tal.getInt("start"), tal.getInt("end"));
		assertEquals("Bar", tal.getString("type"));
	}
}