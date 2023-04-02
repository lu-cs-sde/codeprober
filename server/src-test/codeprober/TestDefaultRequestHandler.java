package codeprober;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.locator.CreateLocator;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.ProbeProtocol;
import codeprober.protocol.create.CreateType;
import codeprober.toolglue.ParseResult;
import codeprober.toolglue.UnderlyingTool;

public class TestDefaultRequestHandler {

	public static class DummyAst {
		public final String data;

		public DummyAst(String data) {
			this.data = data;
		}

		public int getStart() {
			return 0;
		}

		public int getEnd() {
			return 0;
		}

		public int getNumChild() {
			return 0;
		}

		public DummyAst getParent() {
			return null;
		}

		public String getData() { return data; }
	}

	private class DummyTool implements UnderlyingTool {

		protected String extractContents(String file) {
			try {
				final byte[] bytes = Files.readAllBytes(new File(file).toPath());
				return new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);
			} catch (IOException e) {
				System.err.println("Failed reading test file");
				throw new RuntimeException(e);
			}
		}
		@Override
		public ParseResult parse(String[] args) {
			return new ParseResult(new DummyAst(extractContents(args[args.length - 1])));
		}
	}

	@Test
	public void testHandleSemiComplicatedRequest() {
		DefaultRequestHandler handler = new DefaultRequestHandler(new DummyTool(), new String[0]);

		final AstNode ast = new AstNode(TestData.getWithSimpleNta());
		final AstInfo info = TestData.getInfo(ast);

		final JSONObject requestObj = new JSONObject();
		requestObj.put("posRecovery", info.recoveryStrategy.toString());

		final JSONObject query = new JSONObject();
		requestObj.put("query", query);

		query.put("locator", CreateLocator.fromNode(info,
				new AstNode(Reflect.invoke0(ast.underlyingAstNode, "simpleNTA")).getNthChild(info, 1)));

		final JSONObject attr = new JSONObject();
		query.put("attr", attr);

		attr.put("name", "timesTwo");
		final JSONArray args = new JSONArray();
		attr.put("args", args);

		final JSONObject intArg = new JSONObject();
		intArg.put("type", "int");
		intArg.put("value", 21);
		intArg.put("isNodeType", false);
		args.put(intArg);

		final JSONObject retBuilder = new JSONObject();
		final JSONArray bodyBuilder = new JSONArray();

		handler.handleParsedAst(ast.underlyingAstNode, requestObj, retBuilder, bodyBuilder);

		assertEquals(1, bodyBuilder.length());
		assertEquals("42", bodyBuilder.get(0));
	}

	@Test
	public void testPrintStreamArg() {
		DefaultRequestHandler handler = new DefaultRequestHandler(new DummyTool(), new String[0]);

		final AstNode ast = new AstNode(TestData.getWithSimpleNta());
		final AstInfo info = TestData.getInfo(ast);

		final JSONObject requestObj = new JSONObject();
		requestObj.put("posRecovery", info.recoveryStrategy.toString());
		requestObj.put("stdout", true);

		final JSONObject query = new JSONObject();
		requestObj.put("query", query);

		query.put("locator", CreateLocator.fromNode(info, ast));

		final JSONObject attr = new JSONObject();
		query.put("attr", attr);

		attr.put("name", "mthWithPrintStreamArg");
		attr.put("args", new JSONArray().put( //
				CreateType.fromClass(PrintStream.class, info.baseAstClazz).toJson() //
						.put("value", JSONObject.NULL)));

		final JSONObject retBuilder = new JSONObject();
		final JSONArray bodyBuilder = new JSONArray();

		handler.handleParsedAst(ast.underlyingAstNode, requestObj, retBuilder, bodyBuilder);

		assertEquals(2, bodyBuilder.length());
		assertEquals("First msg with linebreak", bodyBuilder.getJSONObject(0).getString("value"));
		assertEquals("Second msg without linebreak", bodyBuilder.getJSONObject(1).getString("value"));
	}

	@Test
	public void testParseCachingWorks() {
		final AtomicInteger parseCounter = new AtomicInteger();
		final AtomicInteger getDataCounter = new AtomicInteger();
		final UnderlyingTool countingTool = new DummyTool() {

			@Override
			public ParseResult parse(String[] args) {
				parseCounter.incrementAndGet();
				final String contents = extractContents(args[args.length - 1]);

				return new ParseResult(new DummyAst(contents) {
					@Override
					public String getData() {
						getDataCounter.incrementAndGet();
						return super.getData();
					}
				});
			}
		};
		DefaultRequestHandler handler = new DefaultRequestHandler(countingTool, new String[0]);

		final JSONObject requestObj = new JSONObject();
		requestObj.put("type", ProbeProtocol.type);
		ProbeProtocol.text.put(requestObj, "Hello World");
		ProbeProtocol.positionRecovery.put(requestObj, PositionRecoveryStrategy.ALTERNATE_PARENT_CHILD.name());
		ProbeProtocol.cache.put(requestObj, AstCacheStrategy.FULL.name());
		ProbeProtocol.tmpSuffix.put(requestObj, ".tmp");

		final JSONObject query = new JSONObject();
		ProbeProtocol.Query.locator.put(query, new JSONObject() //
				.put("result", new JSONObject()) //
				.put("steps", new JSONArray()));
		ProbeProtocol.Query.attribute.put(query, new JSONObject() //
				.put(ProbeProtocol.Attribute.name.key, "getData") //
		);
		ProbeProtocol.query.put(requestObj, query);

		final JSONObject initial = handler.handleRequest(requestObj, null);
		assertEquals(1, parseCounter.get());
		assertEquals(1, getDataCounter.get());
		assertEquals("Hello World", initial.getJSONArray("body").getString(0));

		final JSONObject sameTextShouldBeCached = handler.handleRequest(requestObj, null);
		assertEquals(1, parseCounter.get());
		assertEquals(2, getDataCounter.get());
		assertEquals("Hello World", sameTextShouldBeCached.getJSONArray("body").getString(0));

		ProbeProtocol.text.put(requestObj, "Changed");
		final JSONObject changedText = handler.handleRequest(requestObj, null);
		assertEquals(2, parseCounter.get());
		assertEquals(3, getDataCounter.get());
		assertEquals("Changed", changedText.getJSONArray("body").getString(0));

	}
}
