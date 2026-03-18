package codeprober.textprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.textprobe.TextProbeEnvironment.QueryResult;
import codeprober.textprobe.ast.ASTList;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Query;

public class TestTextProbeEnvironment {

	private AstInfo info;
	private TextProbeEnvironment tpe;
	ASTList<Container> containers;

	private void setup(String src) {
		info = TestData.getInfo(new AstNode(TestData.getSimple()));;
		tpe = new TextProbeEnvironment(info, LLParser.parse(src, '[', ']'));
		containers = tpe.document.containers;;
	}

	@Test
	public void testLabeledProperty() {
		setup("[[Foo.l:x]]");

		assertEquals(1, containers.getNumChild());
		final Query query = containers.get(0).probe().asQuery();

		final QueryResult lhs = tpe.evaluateQuery(query);
		assertEquals(new TestData.Foo(0, 0).cpr_lInvoke("x"), lhs.value);
	}

	@Test
	public void testNormalAssertion() {
		setup("[[Foo.getParent.getClass.getSimpleName=\"Prog\"]]");

		assertEquals(1, containers.getNumChild());
		final Query query = containers.get(0).probe().asQuery();

		final QueryResult lhs = tpe.evaluateQuery(query);
		assertEquals("Program", lhs.value);
		assertFalse(tpe.evaluateComparison(query, lhs));

		assertEquals(1, tpe.errMsgs.size());
		final String compactMsgs = tpe.errMsgs.toString().replace("\n", "|").replace(" ", "");
		assertTrue(compactMsgs, compactMsgs.contains("Expected:\"Prog\"|Actual:\"Program\""));

	}
}
