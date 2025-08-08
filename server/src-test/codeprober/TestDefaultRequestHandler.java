package codeprober;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

import codeprober.ast.TestData;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.data.EvaluatePropertyReq;
import codeprober.protocol.data.EvaluatePropertyRes;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PutWorkspaceContentReq;
import codeprober.protocol.data.PutWorkspaceContentRes;
import codeprober.protocol.data.TALStep;
import codeprober.requesthandler.WorkspaceHandler;
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

		public String getData() {
			return data;
		}
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

	private EvaluatePropertyReq constructEvalRequest(String text, String attrName) {
		return new EvaluatePropertyReq( //
				new ParsingRequestData(PositionRecoveryStrategy.ALTERNATE_PARENT_CHILD, AstCacheStrategy.FULL,
						ParsingSource.fromText(text), null, ".tmp"), //
				new NodeLocator(new TALStep("", "", 0, 0, 0, false), Collections.emptyList()),
				new Property(attrName, Collections.emptyList(), null), false, null, null, null, null);
	}

	private ClientRequest constructRequest(String text, String attrName) {
		return new ClientRequest(constructEvalRequest(text, attrName).toJSON(), obj -> {
		}, new AtomicBoolean(true), (p) -> {
		});
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
		DefaultRequestHandler handler = new DefaultRequestHandler(countingTool);

		final EvaluatePropertyRes initial = EvaluatePropertyRes
				.fromJSON(handler.handleRequest(constructRequest("Hello World", "getData")));
		assertEquals(1, parseCounter.get());
		assertEquals(1, getDataCounter.get());
		assertEquals("Hello World", initial.response.asSync().body.get(0).asPlain());

		final EvaluatePropertyRes sameTextShouldBeCached = EvaluatePropertyRes
				.fromJSON(handler.handleRequest(constructRequest("Hello World", "getData")));
		assertEquals(1, parseCounter.get());
		assertEquals(2, getDataCounter.get());
		assertEquals("Hello World", sameTextShouldBeCached.response.asSync().body.get(0).asPlain());

		final EvaluatePropertyRes changedText = EvaluatePropertyRes
				.fromJSON(handler.handleRequest(constructRequest("Changed", "getData")));
		assertEquals(2, parseCounter.get());
		assertEquals(3, getDataCounter.get());
		assertEquals("Changed", changedText.response.asSync().body.get(0).asPlain());
	}

	@Test
	public void testCprApiStyleHasPrecedenceOverNormal() {
		DefaultRequestHandler handler = new DefaultRequestHandler(
				args -> new ParseResult(new TestData.WithTwoLineVariants(123, 456)));

		final EvaluatePropertyRes resp = EvaluatePropertyRes
				.fromJSON(handler.handleRequest(constructRequest("", "toString")));
		assertEquals((123 << 12) + 123, resp.response.asSync().locator.result.start);
	}

	@Test
	public void testNormalApiStyleIsUsedIfNullCprStyle() {
		DefaultRequestHandler handler = new DefaultRequestHandler(
				args -> new ParseResult(new TestData.WithTwoLineVariants(null, 456)));

		final EvaluatePropertyRes resp = EvaluatePropertyRes
				.fromJSON(handler.handleRequest(constructRequest("", "toString")));
		assertEquals((456 << 12) + 456, resp.response.asSync().locator.result.start);
	}

	@Test
	public void testPutWorkspaceCallback() {
		final DefaultRequestHandler handler = new DefaultRequestHandler(
				args -> new ParseResult(new TestData.WithTwoLineVariants(null, 456)));

		final int[] callbackCount = new int[1];
		final ClientRequest request = new ClientRequest(new PutWorkspaceContentReq("foo/bar", "baz").toJSON(),
				(msg) -> {
				}, new AtomicBoolean(false), path -> {
					assertEquals("foo/bar", path);
					++callbackCount[0];
				});

		// Do it with unchanged workspace handler. It has no workspace directory
		// configured, so request will fail.
		handler.handleRequest(request);
		assertEquals(0, callbackCount[0]);


		WorkspaceHandler.setDefaultWorkspaceHandlerForTesting(new WorkspaceHandler() {
			public PutWorkspaceContentRes handlePutWorkspaceContent(PutWorkspaceContentReq req) {
				return new PutWorkspaceContentRes(true);
			};
		});
		handler.handleRequest(request);
		assertEquals(1, callbackCount[0]);
	}

	@After
	public void restore() {
		WorkspaceHandler.setDefaultWorkspaceHandlerForTesting(new WorkspaceHandler());
	}
}
