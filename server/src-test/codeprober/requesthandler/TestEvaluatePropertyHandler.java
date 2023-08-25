package codeprober.requesthandler;

import static org.junit.Assert.assertEquals;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.locator.CreateLocator;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.data.EvaluatePropertyReq;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.RpcBodyLine;

public class TestEvaluatePropertyHandler {

	protected static LazyParser createHardcodedParser(AstInfo result) {
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
		};
	}

	@Test
	public void testHandleSemiComplicatedRequest() {
		final AstNode ast = new AstNode(TestData.getWithSimpleNta());
		final AstInfo info = TestData.getInfo(ast);

		final EvaluatePropertyReq requestObj = new EvaluatePropertyReq( //
				null, //
				CreateLocator.fromNode(info, ast), //
				new Property("timesTwo", Arrays.asList(PropertyArg.fromInteger(21)), null), //
				true, null, null, null, null);

		final List<RpcBodyLine> body = EvaluatePropertyHandler.apply(requestObj, createHardcodedParser(info)).response.asSync().body;

		assertEquals(1, body.size());
		assertEquals("42", body.get(0).asPlain());
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

		final List<RpcBodyLine> body = EvaluatePropertyHandler.apply(requestObj, createHardcodedParser(info)).response.asSync().body;
		assertEquals(2, body.size());
		assertEquals("First msg with linebreak", body.get(0).asStreamArg());
		assertEquals("Second msg without linebreak", body.get(1).asStreamArg());
	}

}
