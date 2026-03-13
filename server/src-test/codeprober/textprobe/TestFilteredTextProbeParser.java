package codeprober.textprobe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.requesthandler.LazyParser.ParsedAst;
import codeprober.textprobe.FilteredTextProbeParser.FilteredParse;
import codeprober.textprobe.ast.ASTList;
import codeprober.textprobe.ast.Container;

public class TestFilteredTextProbeParser {

	@Test
	public void testSimple() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getSimple()));
		final FilteredParse fp = FilteredTextProbeParser.parse("[[Foo.bar]]\n\n\n [[Baz.qwertyuio]]", new ParsedAst(info));

		final ASTList<Container> containers = fp.document.containers;
		assertEquals(1, containers.getNumChild());
		assertEquals("Foo.bar", containers.get(0).probe().pp());
	}
}
