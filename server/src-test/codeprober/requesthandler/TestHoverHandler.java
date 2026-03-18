package codeprober.requesthandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.After;
import org.junit.Test;

import codeprober.TestDefaultRequestHandler;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.ast.TestData.Foo;
import codeprober.protocol.data.HoverReq;
import codeprober.protocol.data.HoverRes;
import codeprober.textprobe.Parser;
import codeprober.textprobe.TextProbeEnvironment;

public class TestHoverHandler {

	private List<String> doHover(String src, int column) {
		final Object simple = TestData.getSimple();
		final HoverReq gdr = new HoverReq( //
				TestDefaultRequestHandler.createParsingRequestData(src), 1, column //
		);
		final HoverRes ret = HoverHandler.apply(gdr,
				TestEvaluatePropertyHandler.createHardcodedParser(TestData.getInfo(new AstNode(simple))),
				WorkspaceHandler.getDefault());
		return ret.lines;
	}

	private List<String> doHover(String src) {
		return doHover(src, 3);
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

	private String nodeLabeledSrc(boolean withLabel) {
		return String.format("[[Foo.%spropLabel]]", withLabel ? "l:" : "");
	}

	@Test
	public void testHoverLabeledSuccess() {
		Parser.llParser = true;
		for (boolean autoLabel : new boolean[] { true, false }) {
			System.setProperty(TextProbeEnvironment.autoLabelPropertiesKey, String.valueOf(!autoLabel));
			final List<String> labels = doHover(nodeLabeledSrc(autoLabel), 10);
			assertEquals(1, labels.size());
			assertEquals(new Foo(0, 0).cpr_lInvoke("propLabel").toString(), labels.get(0));
		}
	}

	@Test
	public void testHoverLabeledFailure() {
		System.setProperty(TextProbeEnvironment.autoLabelPropertiesKey, "false");
		assertNull(doHover(nodeLabeledSrc(false), 10));
	}

	private String nonNodeLabeledSrc(boolean withLabel) {
		return String.format("[[Foo.nonNode.%snonNodeProp]]", withLabel ? "l:" : "");
	}

	@Test
	public void testHoverNonNodeLabeledSuccess() {
		Parser.llParser = true;
		for (boolean autoLabell : new boolean[] { true, false }) {
			System.setProperty(TextProbeEnvironment.autoLabelPropertiesKey, String.valueOf(!autoLabell));
			final List<String> labels = doHover(nonNodeLabeledSrc(autoLabell), 20);
			assertEquals(1, labels.size());
			assertEquals("lbl_nonNodeProp", labels.get(0));
		}
	}

	@Test
	public void testHoverNonNodeLabeledFailure() {
		System.setProperty(TextProbeEnvironment.autoLabelPropertiesKey, "false");
		assertNull(doHover(nonNodeLabeledSrc(false), 20));
	}

	@After
	public void restore() {
		Parser.llParser = false;
		System.clearProperty(TextProbeEnvironment.autoLabelPropertiesKey);
	}
}
