package codeprober.requesthandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import codeprober.DefaultRequestHandler;
import codeprober.TestDefaultRequestHandler;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.protocol.data.Decoration;
import codeprober.protocol.data.GetDecorationsReq;
import codeprober.protocol.data.GetDecorationsRes;
import codeprober.toolglue.ParseResult;

public class TestDecorationsHandler {

	private Decoration evaluateSimple(String src) {
		final Object simple = TestData.getSimple();
		final GetDecorationsReq gdr = new GetDecorationsReq( //
				TestDefaultRequestHandler.createParsingRequestData(src) //
		);
		final DefaultRequestHandler drh = new DefaultRequestHandler( //
				(args) -> new ParseResult(simple) //
		);
		final GetDecorationsRes ret = DecorationsHandler.apply(gdr, drh, WorkspaceHandler.getDefault(),
				TestEvaluatePropertyHandler.createHardcodedParser(TestData.getInfo(new AstNode(simple))));
		assertNotNull(ret.lines);
		assertEquals(1, ret.lines.size());
		return ret.lines.get(0);
	}

	@Test
	public void testOk() {
		final String src = "[[Foo.foobarAttr~=Foo and Bar]]";
		final Decoration line = evaluateSimple(src);
		assertEquals("ok", line.type);
		assertEquals((1 << 12) + 1, line.start);
		assertEquals((1 << 12) + src.length(), line.end);
	}

	@Test
	public void testError() {
		final String src = "[[Foo.badAttr!=This attr doesn't exist!]]";
		final Decoration line = evaluateSimple(src);
		assertEquals("error", line.type);
		assertEquals((1 << 12) + 1, line.start);
		assertEquals((1 << 12) + src.length(), line.end);
	}

	@Test
	public void testVarDef() {
		final String src = "[[$f:=Foo]]";
		final Decoration line = evaluateSimple(src);
		assertEquals("var", line.type);
		assertEquals((1 << 12) + 1, line.start);
		assertEquals((1 << 12) + src.length(), line.end);
	}
}
