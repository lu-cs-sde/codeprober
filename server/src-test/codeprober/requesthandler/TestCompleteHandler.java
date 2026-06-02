package codeprober.requesthandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Test;

import codeprober.TestDefaultRequestHandler;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.protocol.data.CompleteReq;
import codeprober.protocol.data.CompleteRes;
import codeprober.protocol.data.CompletionItem;
import codeprober.textprobe.Parser;
import codeprober.textprobe.TextProbeEnvironment;

public class TestCompleteHandler {

	private CompleteRes doCompleteRaw(String src, int column) {
		final Object simple = TestData.getSimple();
		final CompleteReq gdr = new CompleteReq( //
				TestDefaultRequestHandler.createParsingRequestData(src), 1, column //
		);
		return CompleteHandler.apply(gdr,
				TestEvaluatePropertyHandler.createHardcodedParser(TestData.getInfo(new AstNode(simple))),
				WorkspaceHandler.getDefault());
	}

	private List<String> doComplete(String src, int column) {
		return doCompleteRaw(src, column).lines.stream().map(x -> x.label).collect(Collectors.toList());
	}

	private String nodeLabelSrc(boolean withLabel) {
		return String.format("[[Foo.%spropLabel]]", withLabel ? "l:" : "");
	}

	@Test
	public void testCompleteLabeledNode() {
		Parser.llParser = true;
		for (boolean autoLabel : new boolean[] { true, false }) {
			System.setProperty(TextProbeEnvironment.autoLabelPropertiesKey, String.valueOf(autoLabel));
			final List<String> labels = doComplete(nodeLabelSrc(!autoLabel), 10);
			final boolean hasSugar = labels.contains("propLabel");
			final boolean hasRaw = labels.contains("l:propLabel");
			assertNotEquals(hasSugar, hasRaw);
			assertEquals(autoLabel, hasSugar);
		}
	}

	private String nonNodeLabelSrc(boolean withLabel) {
		return String.format("[[Foo.nonNode.%snonNodeProp]]", withLabel ? "l:" : "");
	}

	@Test
	public void testCompleteLabeledNonNode() {
		Parser.llParser = true;
		for (boolean autoLabel : new boolean[] { true, false }) {
			System.setProperty(TextProbeEnvironment.autoLabelPropertiesKey, String.valueOf(autoLabel));
			final List<String> labels = doComplete(nonNodeLabelSrc(!autoLabel), 20);
			final boolean hasSugar = labels.contains("nonNodeProp");
			final boolean hasRaw = labels.contains("l:nonNodeProp");
			assertNotEquals(hasSugar, hasRaw);
			assertEquals(autoLabel, hasSugar);
		}
	}

	@Test
	public void testCompleteNewArg() {
		final List<CompletionItem> labels = doCompleteRaw("[[Foo.equals()]]", 14).lines;
		assertEquals(2, labels.size());

		assertEquals("TestData$Program", labels.get(0).insertText);
		assertEquals("TestData$Foo", labels.get(1).insertText);
		for (CompletionItem ci : labels) {
			assertEquals((1 << 12) + 14, ci.insertStart.intValue());
			assertEquals((1 << 12) + 14, ci.insertEnd.intValue());
		}
	}

	@After
	public void restore() {
		Parser.llParser = false;
		System.clearProperty(TextProbeEnvironment.autoLabelPropertiesKey);
	}
}
