package codeprober.locator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.ast.TestData.Node;
import codeprober.ast.TestData.Program;
import codeprober.metaprogramming.Reflect;
import codeprober.metaprogramming.TypeIdentificationStyle;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.NodeLocatorStep;
import codeprober.protocol.data.NullableNodeLocator;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.TALStep;

public class TestCreateLocator {

	private void assertSpan(Span expected, int actualStart, int actualEnd) {
		assertEquals(expected.start, actualStart);
		assertEquals(expected.end, actualEnd);
	}

	private NodeLocator createLocator(AstInfo info, AstNode node) {
		return CreateLocator.fromNode(info, node);
	}

	@Before
	public void setup() {
		// The cache is an optimisation that enables a different code path in
		// CreateLocator. Can be uncommented, should work identically but tests less
		// code.
		CreateLocator.identityLocatorCache = new IdentityHashMap<>();
	}

	@Test
	public void testSimpleRoot() {
		AstNode root = new AstNode(TestData.getSimple());
		final AstInfo info = TestData.getInfo(root);

		final NodeLocator locator = createLocator(info, root);

		assertSpan(root.getRawSpan(info), locator.result.start, locator.result.end);
		assertEquals(TestData.Program.class.getName(), locator.result.type);

		assertEquals(0, locator.steps.size());
	}

	@Test
	public void testSimpleFoo() {
		final AstNode root = new AstNode(TestData.getSimple());
		final AstInfo info = TestData.getInfo(root);
		final AstNode foo = root.getNthChild(info, 0);

		final NodeLocator locator = createLocator(info, foo);

		final Consumer<TALStep> assertFooLoc = (res) -> {
			assertSpan(foo.getRawSpan(info), res.start, res.end);
			assertEquals(TestData.Foo.class.getName(), res.type);
		};
		assertFooLoc.accept(locator.result);

		final List<NodeLocatorStep> steps = locator.steps;
		assertEquals(1, steps.size());

		assertFooLoc.accept(steps.get(0).asTal());
	}

	@Test
	public void testSimpleBar() {
		final AstNode root = new AstNode(TestData.getSimple());
		final AstInfo info = TestData.getInfo(root);
		final AstNode bar = root.getNthChild(info, 0).getNthChild(info, 0);

		final NodeLocator locator = createLocator(info, bar);

		final Consumer<TALStep> assertBarLoc = (res) -> {
			assertSpan(bar.getRawSpan(info), res.start, res.end);
			assertEquals(TestData.Bar.class.getName(), res.type);
		};
		assertBarLoc.accept(locator.result);

		final List<NodeLocatorStep> steps = locator.steps;
		// Only 1 because the Program->Foo step is unnecessary.
		assertEquals(1, steps.size());

		final NodeLocatorStep step = steps.get(0);
		assertTrue(step.isTal());

		assertBarLoc.accept(step.asTal());
	}

	@Test
	public void testAmbiguousBar() {
		final AstNode root = new AstNode(TestData.getFlatAmbiguous());
		final AstInfo info = TestData.getInfo(root);
		final AstNode foo = root.getNthChild(info, 0);
		final AstNode bar = foo.getNthChild(info, 1);

		final NodeLocator locator = createLocator(info, bar);

		final Consumer<TALStep> assertBarLoc = (res) -> {
			assertSpan(bar.getRawSpan(info), res.start, res.end);
			assertEquals(TestData.Bar.class.getName(), res.type);
		};
		assertBarLoc.accept(locator.result);

		final List<NodeLocatorStep> steps = locator.steps;
		assertEquals(2, steps.size());

		final NodeLocatorStep fooStep = steps.get(0);
		assertTrue(fooStep.isTal());
		final TALStep fooLocator = fooStep.asTal();
		assertSpan(foo.getRawSpan(info), fooLocator.start, fooLocator.end);
		assertEquals(TestData.Foo.class.getName(), fooLocator.type);

		final NodeLocatorStep barStep = steps.get(1);
		assertTrue(barStep.isChild());
		assertEquals(1, barStep.asChild());
	}

	@Test
	public void testSimpleNta() {
		final AstNode root = new AstNode(TestData.getWithSimpleNta());
		final AstInfo info = TestData.getInfo(root);
		final AstNode foo = new AstNode(Reflect.invoke0(info.ast.underlyingAstNode, "simpleNTA"));

		final NodeLocator locator = createLocator(info, foo);

		final TALStep res = locator.result;
		assertSpan(foo.getRecoveredSpan(info), res.start, res.end);
		assertEquals(TestData.Foo.class.getName(), res.type);

		final List<NodeLocatorStep> steps = locator.steps;
		assertEquals(1, steps.size());

		final NodeLocatorStep ntaStep = steps.get(0);
		assertTrue(ntaStep.isNta());

		final Property nta = ntaStep.asNta().property;
		assertEquals("simpleNTA", nta.name);
		assertEquals(0, nta.args.size());

	}

	@Test
	public void testParameterizedNtaFoo() {
		final AstNode root = new AstNode(TestData.getWithParameterizedNta());
		final AstInfo info = TestData.getInfo(root);
		final AstNode bar = new AstNode(Reflect.invokeN(info.ast.underlyingAstNode, "parameterizedNTA",
				new Class<?>[] { Integer.TYPE, info.baseAstClazz },
				new Object[] { 1, info.ast.getNthChild(info, 0).underlyingAstNode })).getNthChild(info, 1);

		final NodeLocator locator = createLocator(info, bar);

		final TALStep res = locator.result;
		assertSpan(bar.getRecoveredSpan(info), res.start, res.end);
		assertEquals(TestData.Bar.class.getName(), res.type);

		final List<NodeLocatorStep> steps = locator.steps;
		assertEquals(2, steps.size());

		final Property nta = steps.get(0).asNta().property;
		assertEquals("parameterizedNTA", nta.name);
		final List<PropertyArg> args = nta.args;
		assertEquals(2, args.size());

		assertEquals(1, args.get(0).asInteger());

		final NullableNodeLocator nodeArg = args.get(1).asNodeLocator();
		assertEquals(info.baseAstClazz.getName(), nodeArg.type);

		final NodeLocator nodeLocator = nodeArg.value;
		Consumer<TALStep> assertFooLocator = (fooLoc) -> {
			assertSpan(info.ast.getNthChild(info, 0).getRecoveredSpan(info), fooLoc.start, fooLoc.end);
			assertEquals(TestData.Foo.class.getName(), fooLoc.type);
		};
		assertFooLocator.accept(nodeLocator.result);

		final List<NodeLocatorStep> nodeLocatorSteps = nodeLocator.steps;
		assertEquals(1, nodeLocatorSteps.size());
		assertFooLocator.accept(nodeLocatorSteps.get(0).asTal());

		assertEquals(1, steps.get(1).asChild());
	}

	@Test
	public void testNestedParameterizedNta() {
		final Program underlyingRoot = TestData.getWithNestedParameterizedNta();
		final AstNode root = new AstNode(underlyingRoot);
		final AstInfo info = TestData.getInfo(root);

		final Node ntaArg = underlyingRoot.getChild(0);
		final Node inner = underlyingRoot //
				.parameterizedNTA(1, ntaArg) //
				.getChild(0) //
				.getChild(0) //
				.parameterizedNTA(2, ntaArg);

		final NodeLocator locator = CreateLocator.fromNode(info, new AstNode(inner));

		assertEquals(3, locator.steps.size());

		assertEquals(NodeLocatorStep.Type.nta, locator.steps.get(0).type);
		assertEquals(NodeLocatorStep.Type.tal, locator.steps.get(1).type);
		assertEquals(NodeLocatorStep.Type.nta, locator.steps.get(2).type);

		assertSame(inner, ApplyLocator.toNode(info, locator).node.underlyingAstNode);
	}

	@Test
	public void testAmbiguousUncle() {
		AstNode root = new AstNode(TestData.getAmbiguousUncle());
		final AstInfo info = TestData.getInfo(root);
		final AstNode bar = root.getNthChild(info, 1).getNthChild(info, 0);

		final NodeLocator locator = createLocator(info, bar);

		final TALStep res = locator.result;
		assertSpan(bar.getRawSpan(info), res.start, res.end);
		assertEquals(TestData.Bar.class.getName(), res.type);

		final List<NodeLocatorStep> steps = locator.steps;
		assertEquals(1, steps.size());

		final TALStep tal = steps.get(0).asTal();
		assertSpan(bar.getRawSpan(info), tal.start, tal.end);
		assertEquals(TestData.Bar.class.getName(), tal.type);
	}

	@Test
	public void testAmbigHierarchyRequiringTwoSequentialTal() {
		AstNode root = new AstNode(TestData.getIdenticalBarsWithDifferentGrandParents());
		final AstInfo info = TestData.getInfo(root);
		final AstNode qux = root.getNthChild(info, 1);
		final AstNode bar = qux.getNthChild(info, 0).getNthChild(info, 0);

		final NodeLocator locator = createLocator(info, bar);

		final TALStep res = locator.result;
		assertSpan(bar.getRawSpan(info), res.start, res.end);
		assertEquals(TestData.Bar.class.getName(), res.type);

		final List<NodeLocatorStep> steps = locator.steps;
		assertEquals(2, steps.size());

		final TALStep quxTal = steps.get(0).asTal();
		assertSpan(qux.getRawSpan(info), quxTal.start, quxTal.end);
		assertEquals(TestData.Qux.class.getName(), quxTal.type);

		final TALStep barTal = steps.get(1).asTal();
		assertSpan(bar.getRawSpan(info), barTal.start, barTal.end);
		assertEquals(TestData.Bar.class.getName(), barTal.type);
	}

	@Test
	public void testExternalFileFlag() {
		final AstNode root = new AstNode(TestData.getIdenticalBarsWithDifferentGrandParents());
		final AstInfo info = TestData.getInfo(root);
		final AstNode qux = root.getNthChild(info, 1);

		final NodeLocator locator = createLocator(info, qux);

		assertEquals(true, locator.result.external);

		final List<NodeLocatorStep> steps = locator.steps;
		assertEquals(1, steps.size());

		final NodeLocatorStep step = steps.get(0);
		assertTrue(step.isTal());

		final TALStep tal = step.asTal();
		assertSpan(qux.getRawSpan(info), tal.start, tal.end);
		assertEquals(TestData.Qux.class.getName(), tal.type);
		assertEquals(true, tal.external);
	}

	@Test
	public void testCreateLabeled() {
		final AstNode root = new AstNode(TestData.getWithLabels());

		final BiFunction<NodeLocator, NodeLocatorStep.Type, NodeLocator> assertLocator = (locator,
				expectedSingleStepType) -> {
			final List<NodeLocatorStep> steps = locator.steps;
			assertEquals(1, steps.size());

			final NodeLocatorStep step = steps.get(0);
			assertEquals(expectedSingleStepType, step.type);

			return locator;
		};

		final BiConsumer<TypeIdentificationStyle, NodeLocatorStep.Type> testIdentifications = (style,
				expectedLbl2Type) -> {
			final AstInfo info = TestData.getInfo(root, style);

			final AstNode lbl1 = root.getNthChild(info, 0);
			assertSame(lbl1, ApplyLocator.toNode(info,
					assertLocator.apply(createLocator(info, lbl1), NodeLocatorStep.Type.tal)).node);

			final AstNode lbl2 = root.getNthChild(info, 1);
			assertSame(lbl2,
					ApplyLocator.toNode(info, assertLocator.apply(createLocator(info, lbl2), expectedLbl2Type)).node);
		};

		testIdentifications.accept(TypeIdentificationStyle.REFLECTION, NodeLocatorStep.Type.child);
		if (CreateLocator.identityLocatorCache != null) {
			// Swapping identification style without restarting the JVM is not usually
			// supported.
			// Must manually clear the locator cache.
			CreateLocator.identityLocatorCache.clear();
		}
		testIdentifications.accept(TypeIdentificationStyle.NODE_LABEL, NodeLocatorStep.Type.tal);
	}

	@Test
	public void testIdentifyingNoTransformChildren() {
		final TestData.Node underlyingRoot = TestData.getWithTransformedChildren();
		final AstNode root = new AstNode(underlyingRoot);
		final AstInfo info = TestData.getInfo(root);

		final AstNode foo = root.getNthChild(info, 0);
		final NodeLocator fooLocator = createLocator(info, foo);
		assertEquals(1, fooLocator.steps.size());
		assertEquals(NodeLocatorStep.Type.tal, fooLocator.steps.get(0).type);

		final AstNode bar = new AstNode(underlyingRoot.getChildNoTransform(0));
		final NodeLocator barLocator = createLocator(info, bar);
		assertEquals(1, barLocator.steps.size());
		final NodeLocatorStep barStep = barLocator.steps.get(0);
		assertEquals(NodeLocatorStep.Type.nta, barStep.type);
		assertEquals("getChildNoTransform", barStep.asNta().property.name);
		assertEquals(0, barStep.asNta().property.args.get(0).value);
		assertSame(bar.underlyingAstNode, ApplyLocator.toNode(info, barLocator).node.underlyingAstNode);

		final AstNode qux = new AstNode(underlyingRoot.getChildNoTransform(1));
		final NodeLocator quxLocator = createLocator(info, qux);
		assertEquals(1, quxLocator.steps.size());
		final NodeLocatorStep quxStep = quxLocator.steps.get(0);
		assertEquals(NodeLocatorStep.Type.nta, quxStep.type);
		assertEquals("getChildNoTransform", quxStep.asNta().property.name);
		assertEquals(1, quxStep.asNta().property.args.get(0).value);
		assertSame(qux.underlyingAstNode, ApplyLocator.toNode(info, quxLocator).node.underlyingAstNode);
	}

	@Test
	public void testNtaWithInterfaceArgument() {
		final TestData.Node root = new TestData.Program(0, 0);
		final TestData.Foo child = new TestData.Foo(0, 0);
		final TestData.RunNode argNode = new TestData.RunNode(0, 0);
		root.add(argNode);
		final AstInfo info = TestData.getInfo(new AstNode(root));

		root.setRunnableParameterizedNTA(argNode, child);
		final NodeLocator locator = createLocator(info, new AstNode(child));

		assertEquals(1, locator.steps.size());

		final NodeLocatorStep step = locator.steps.get(0);
		assertEquals(NodeLocatorStep.Type.nta, step.type);
		final Property nta = step.asNta().property;

		assertEquals("runnableParameterizedNTA", nta.name);
		assertEquals(1, nta.args.size());

		final PropertyArg arg = nta.args.get(0);
		assertEquals(PropertyArg.Type.nodeLocator, arg.type);
		assertEquals("java.lang.Runnable", arg.asNodeLocator().type);
		final NodeLocator argLoc = arg.asNodeLocator().value;
		assertEquals(TestData.RunNode.class.getName(), argLoc.result.type);
		assertEquals(1, argLoc.steps.size());
		assertEquals(NodeLocatorStep.Type.tal, argLoc.steps.get(0).type);
		assertSame(argNode, ApplyLocator.toNode(info, argLoc).node.underlyingAstNode);

		assertSame(child, ApplyLocator.toNode(info, locator).node.underlyingAstNode);
	}
}
