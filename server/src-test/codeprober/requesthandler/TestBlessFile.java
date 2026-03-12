package codeprober.requesthandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.Test;

import codeprober.TestDefaultRequestHandler;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.protocol.data.BlessFileReq;
import codeprober.protocol.data.BlessFileRes;

public class TestBlessFile {

	private BlessFileRes evaluate(String src, BlessFileMode mode) {
		final Object simple = TestData.getSimple();
		final BlessFileReq gdr = new BlessFileReq( //
				TestDefaultRequestHandler.createParsingRequestData(src), //
				mode);
		return BlessFileHandler.apply(gdr, WorkspaceHandler.getDefault(),
				TestEvaluatePropertyHandler.createHardcodedParser(TestData.getInfo(new AstNode(simple))));
	}

	private BlessFileRes evaluateSimple(String src) {
		return evaluate(src, BlessFileMode.ECHO_RESULT);
	}

	@Test
	public void testOk() {
		final String src = "[[Foo.foobarAttr=\"Needs Blessing\"]]";
		final BlessFileRes res = evaluateSimple(src);
		assertEquals((Object) 1, res.numUpdatedProbes);
		assertEquals("[[Foo.foobarAttr=\"Only in Foo and Bar\"]]", res.result);
	}

	@Test
	public void testWasAlreadyOk() {
		final String src = "[[Foo.foobarAttr=\"Only in Foo and Bar\"]]";
		final BlessFileRes res = evaluateSimple(src);
		assertEquals((Object) 0, res.numUpdatedProbes);
	}

	@Test
	public void testStaticError() {
		final String src = "[[Foo.foobarAttr=$unexisting]]";
		final BlessFileRes res = evaluateSimple(src);
		assertNull(res.numUpdatedProbes);
		assertEquals("[1:18->1:28] No such var 'unexisting'", res.result);
	}

	@Test
	public void testDynamicError() {
		final String src = "[[Foo.noSuchAttr=\"foo\"]]";
		final BlessFileRes res = evaluateSimple(src);
		assertNull(res.numUpdatedProbes);
		assertEquals("[1:7->1:16] No such attribute 'noSuchAttr' on codeprober.ast.TestData$Foo", res.result);
	}

	@Test
	public void testReplaceToInteger() {
		final String src = "[[Foo.getNumChild=\"str\"]]";
		final BlessFileRes res = evaluateSimple(src);
		assertEquals((Object) 1, res.numUpdatedProbes);
		assertEquals("[[Foo.getNumChild=1]]", res.result);
	}

	@Test
	public void testReplaceFromInteger() {
		final String src = "[[Foo.foobarAttr=123]]";
		final BlessFileRes res = evaluateSimple(src);
		assertEquals((Object) 1, res.numUpdatedProbes);
		assertEquals("[[Foo.foobarAttr=\"Only in Foo and Bar\"]]", res.result);
	}

	@Test
	public void testReplaceFromEmptyString() {
		final String src = "[[Foo.foobarAttr=]]";
		final BlessFileRes res = evaluateSimple(src);
		assertEquals((Object) 1, res.numUpdatedProbes);
		assertEquals("[[Foo.foobarAttr=\"Only in Foo and Bar\"]]", res.result);
	}

	@Test
	public void testWithUnrelatedContents() {
		final String src = "abc\npre[[Foo.foobarAttr=\"Needs Blessing\"]]post\nxyz";
		final BlessFileRes res = evaluateSimple(src);
		assertEquals((Object) 1, res.numUpdatedProbes);
		assertEquals("abc\npre[[Foo.foobarAttr=\"Only in Foo and Bar\"]]post\nxyz", res.result);

	}

	@Test
	public void testMultipleThingsOnMultipleLines() {
		final String src = Arrays.asList(//
				"[[Foo.timesTwo(5)=1]][[Foo=\"Bar\"]]", //
				"[[^Foo.timesTwo(7)=10]][[^Program=^Foo]]" //
		).stream().collect(Collectors.joining("\n"));
		final BlessFileRes res = evaluateSimple(src);
		assertEquals((Object) 3, res.numUpdatedProbes);
		assertEquals(Arrays.asList(//
				"[[Foo.timesTwo(5)=10]][[Foo=\"TestData$Foo\"]]", //
				"[[^Foo.timesTwo(7)=14]][[^Program=^Foo]]" //
		).stream().collect(Collectors.joining("\n")), res.result);
	}

	@Test
	public void testDryRun() {
		final String src = "[[Foo.foobarAttr=\"Pre\"]] [[Foo.getNumChild=123]]";
		final BlessFileRes res = evaluate(src, BlessFileMode.DRY_RUN);
		assertEquals((Object) 2, res.numUpdatedProbes);
		assertEquals("[1:18->1:23] '\"Pre\"' -> '\"Only in Foo and Bar\"'\n[1:44->1:47] '123' -> '1'", res.result);
	}

	@Test
	public void testUnicodeCharacters() {
		final String src = "👈[[Foo.getNumChild=\"😎\"]]👉";
		final BlessFileRes res = evaluateSimple(src);
		assertEquals((Object) 1, res.numUpdatedProbes);
		assertEquals("👈[[Foo.getNumChild=1]]👉", res.result);
	}

	@Test
	public void testIgnoreNonEqOperators() {
		final String src = "[[Foo.getNumChild~=5]] [[Foo.foobarAttr!=\"Only in Foo and Bar\"]] [[Foo.getNumChild!~=1]]";
		final BlessFileRes res = evaluateSimple(src);
		assertEquals((Object) 0, res.numUpdatedProbes);
	}
}
