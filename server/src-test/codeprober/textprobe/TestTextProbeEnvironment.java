package codeprober.textprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.textprobe.TextProbeEnvironment.QueryResult;
import codeprober.textprobe.ast.ASTList;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Probe;
import codeprober.textprobe.ast.Query;

public class TestTextProbeEnvironment {

	private AstInfo info;
	private TextProbeEnvironment tpe;
	ASTList<Container> containers;
	Probe probe;

	private void setupSingle(String src) {
		info = TestData.getInfo(new AstNode(TestData.getSimple()));
		tpe = new TextProbeEnvironment(info, LLParser.parse(src, '[', ']'));
		containers = tpe.document.containers;;
		assertEquals(1, containers.getNumChild());
		probe = containers.get(0).probe();
	}

	@Test
	public void testLabeledProperty() {
		setupSingle("[[Foo.l:x]]");
		final QueryResult lhs = tpe.evaluateQuery(probe.asQuery());
		assertEquals(new TestData.Foo(0, 0).cpr_lInvoke("x"), lhs.value);
	}

	@Test
	public void testNormalAssertion() {
		setupSingle("[[Foo.getParent.getClass.getSimpleName=\"Prog\"]]");
		final Query query = probe.asQuery();

		final QueryResult lhs = tpe.evaluateQuery(query);
		assertEquals("Program", lhs.value);
		assertFalse(tpe.evaluateComparison(query, lhs));

		assertEquals(1, tpe.errMsgs.size());
		final String compactMsgs = tpe.errMsgs.toString().replace("\n", "|").replace(" ", "");
		assertTrue(compactMsgs, compactMsgs.contains("Expected:\"Prog\"|Actual:\"Program\""));
	}

	@Test
	public void testAutoLabelled() {
		System.setProperty(TextProbeEnvironment.autoLabelPropertiesKey, "true");
		setupSingle("[[Foo.propLabel]]");
		final Query query = probe.asQuery();
		final QueryResult lhs = tpe.evaluateQuery(query);
		assertEquals(new TestData.Foo(0, 0).cpr_lInvoke("propLabel"), lhs.value);
	}

	@After
	public void restore() {
		System.clearProperty(TextProbeEnvironment.autoLabelPropertiesKey);
	}
}
