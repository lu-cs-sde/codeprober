package codeprober.requesthandler;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import codeprober.TestDefaultRequestHandler;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.protocol.data.HoverReq;
import codeprober.protocol.data.HoverRes;

public class TestHoverHandler {

	private List<String> doHover(String src) {
		final Object simple = TestData.getSimple();
		final HoverReq gdr = new HoverReq( //
				TestDefaultRequestHandler.createParsingRequestData(src), 1, 3 //
		);
		final HoverRes ret = HoverHandler.apply(gdr,
				TestEvaluatePropertyHandler.createHardcodedParser(TestData.getInfo(new AstNode(simple))),
				WorkspaceHandler.getDefault());
		return ret.lines;
	}

	@Test
	public void testHoverInvalidNode() {
		assertEquals(0, doHover("[[InvalidNode]]").size());
	}

	@Test
	public void testHoverValidNode() {
		assertEquals(1, doHover("[[Foo]]").size());
	}

	@Test
	public void testHoverNonexistingVar() {
		// When there are semantic errors, everything should shut down (=> null)
		assertEquals(null, doHover("[[$foo]]"));
	}

	@Test
	public void testHoverGoodVar() {
		assertEquals(1, doHover("[[$foo]] [[$foo:=Foo]]").size());
	}
	@Test

	public void testHoverBarVar() {
		assertEquals(0, doHover("[[$foo]] [[$foo:=InvalidNode]]").size());
	}
}
