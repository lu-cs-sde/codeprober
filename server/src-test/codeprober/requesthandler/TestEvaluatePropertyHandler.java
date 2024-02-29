package codeprober.requesthandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.ast.TestData.Node;
import codeprober.locator.CreateLocator;
import codeprober.locator.CreateLocator.LocatorMergeMethod;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.data.EvaluatePropertyReq;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.RpcBodyLine;

public class TestEvaluatePropertyHandler {

	protected static LazyParser createHardcodedParser(AstInfo result) {
		return createHardcodedParser(result, new AtomicBoolean());
	}

	protected static LazyParser createHardcodedParser(AstInfo result, AtomicBoolean discarCachedAstWasCalled) {
		final LazyParser.ParsedAst wrapped = new LazyParser.ParsedAst(result, 0L, Collections.emptyList());
		return new LazyParser() {

			@Override
			public ParsedAst parse(String inputText, AstCacheStrategy optCacheStrategyVal,
					List<String> optArgsOverrideVal, PositionRecoveryStrategy posRecovery, String tmpFileSuffix) {
				return wrapped;
			}

			@Override
			public ParsedAst parse(ParsingRequestData prd) {
				return wrapped;
			}

			@Override
			public void discardCachedAst() {
				discarCachedAstWasCalled.set(true);
			}
		};
	}

	@Test
	public void testHandleSemiComplicatedRequest() {
		final AstNode ast = new AstNode(TestData.getWithSimpleNta());
		final AstInfo info = TestData.getInfo(ast);
		final AtomicBoolean calledDiscardAst = new AtomicBoolean();

		final EvaluatePropertyReq requestObj = new EvaluatePropertyReq( //
				null, //
				CreateLocator.fromNode(info, ast), //
				new Property("timesTwo", Arrays.asList(PropertyArg.fromInteger(21)), null), //
				true, null, null, null, null);

		final List<RpcBodyLine> body = EvaluatePropertyHandler.apply(requestObj, createHardcodedParser(info, calledDiscardAst)).response
				.asSync().body;

		assertEquals(1, body.size());
		assertEquals("42", body.get(0).asPlain());
		assertFalse(calledDiscardAst.get());
	}

	@Test
	public void testPrintStreamArg() {
		final AstNode ast = new AstNode(TestData.getWithSimpleNta());
		final AstInfo info = TestData.getInfo(ast);

		final EvaluatePropertyReq requestObj = new EvaluatePropertyReq( //
				null, //
				CreateLocator.fromNode(info, ast), //
				new Property("mthWithPrintStreamArg",
						Arrays.asList(PropertyArg.fromOutputstream(PrintStream.class.getName())), null), //
				true, null, null, null, null);

		final List<RpcBodyLine> body = EvaluatePropertyHandler.apply(requestObj, createHardcodedParser(info)).response
				.asSync().body;
		assertEquals(2, body.size());
		assertEquals("First msg with linebreak", body.get(0).asStreamArg());
		assertEquals("Second msg without linebreak", body.get(1).asStreamArg());
	}

	@Test
	public void testCapturesStdout() {
		final TestData.Program underlyingAst = new TestData.Program(0, 0);
		final AstNode ast = new AstNode(underlyingAst);
		final AstInfo info = TestData.getInfo(ast);

		final EvaluatePropertyReq requestObj = new EvaluatePropertyReq( //
				null, //
				CreateLocator.fromNode(info, ast), //
				new Property("onDemandNTA", Collections.emptyList(), null), //
				true, null, null, null, null);

		final List<RpcBodyLine> body = EvaluatePropertyHandler.apply(requestObj, createHardcodedParser(info)).response
				.asSync().body;
		assertEquals(3, body.size());
		assertEquals("Calling onDemandNTA", body.get(0).asStdout());

		final NodeLocator expectedLocator = CreateLocator.fromNode(info, new AstNode(underlyingAst.onDemandNTA()));
		final NodeLocator actualLocator = body.get(1).asNode();
		assertEquals(expectedLocator.toJSON().toString(), actualLocator.toJSON().toString());

		assertEquals("\n", body.get(2).asPlain());
	}

	/**
	 * Regression test for tracing flush leading to an "couldn't create locator"
	 * issue. The steps the server took to create the issue:
	 * <ol>
	 * <li>node := apply_locator
	 * <li>flush_tree (for tracing)
	 * <li>val := eval_property
	 * <li>output := encode(val)
	 * </ol>
	 * The last step might fail. If val is an AST node that is a descendant of
	 * "node", then creating a locator for it may fail, if "node" is (or is a
	 * descendent of) an NTA, since the NTA link was flushed away.
	 * <p>
	 * The solution is to do things in slightly different order so that flushing
	 * always happens before apply_locator. Care should be taken such that
	 * "captureStdout" doesn't capture data from apply_locator, and this is tested
	 * by this case. too.
	 */
	@Test
	public void testTracingFlushNotRetainingInvalidNodePointer() {
		final TestData.Program underlyingAst = new TestData.Program(0, 0);
		final AstNode ast = new AstNode(underlyingAst);
		final AstInfo info = TestData.getInfo(ast);

		final Node sourceNode = underlyingAst.onDemandNTA();
		CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
		final NodeLocator expectedLocator = CreateLocator.fromNode(info, new AstNode(sourceNode.getChild(0)));
		CreateLocator.setMergeMethod(LocatorMergeMethod.DEFAULT_METHOD);

		final EvaluatePropertyReq requestObj = new EvaluatePropertyReq( //
				null, //
				CreateLocator.fromNode(info, new AstNode(sourceNode)), //
				new Property("getChild", Arrays.asList(PropertyArg.fromInteger(0)), null), //
				true, null, null, null, true, true);

		final List<RpcBodyLine> body = EvaluatePropertyHandler.apply(requestObj, createHardcodedParser(info)).response
				.asSync().body;
		assertEquals(2, body.size());
		assertEquals(expectedLocator.toJSON().toString(), body.get(0).asNode().toJSON().toString());
		assertEquals("\n", body.get(1).asPlain());
	}

	@Test
	public void testCallsDiscardOnException() {
		final TestData.Program underlyingAst = new TestData.Program(0, 0);
		final AstNode ast = new AstNode(underlyingAst);
		final AstInfo info = TestData.getInfo(ast);

		final EvaluatePropertyReq requestObj = new EvaluatePropertyReq( //
				null, //
				CreateLocator.fromNode(info, ast), //
				new Property("throwRuntimeException", Collections.emptyList(), null), true);

		final AtomicBoolean calledDiscard = new AtomicBoolean();
		EvaluatePropertyHandler.apply(requestObj, createHardcodedParser(info, calledDiscard));
		assertTrue(calledDiscard.get());
	}
}
