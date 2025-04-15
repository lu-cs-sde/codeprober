package codeprober.requesthandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.locator.CreateLocator;
import codeprober.protocol.data.Tracing;

public class TestTracingBuilder {

	private static AstInfo getDummyInfo() {
		return TestData.getInfo(new AstNode(TestData.getSimple()));
	}

	private static Object[] begin(AstInfo info, String func) {
		return new Object[] { "COMPUTE_BEGIN", info.ast.underlyingAstNode, String.format("A.%s()", func), "", "" };
	}
	private static Object[] end(AstInfo info, String func, Object result) {
		return new Object[] { "COMPUTE_END", info.ast.underlyingAstNode, String.format("A.%s()", func), "", result };
	}

	@Test
	public void testBasicUsage() {
		final AstInfo info = getDummyInfo();
		TracingBuilder tb = new TracingBuilder(info);

		tb.accept(begin(info, "foo"));
		tb.accept(begin(info, "bar"));
		tb.accept(end(info, "bar", "BarResult"));
		tb.accept(end(info, "foo", "FooResult"));

		tb.stop();
		final Tracing result = tb.finish(CreateLocator.fromNode(info, info.ast));

		assertEquals("foo()", result.prop.name);
		assertEquals(1, result.dependencies.size());
		assertEquals("FooResult", result.result.asPlain());

		final Tracing dep = result.dependencies.get(0);
		assertEquals("bar()", dep.prop.name);
		assertEquals("BarResult", dep.result.asPlain());
	}

	@Test
	public void testRecoverFromMissingEnd() {
		final AstInfo info = getDummyInfo();
		TracingBuilder tb = new TracingBuilder(info);

		// Intentional missing "end" for most events here
		tb.accept(begin(info, "foo"));
		tb.accept(begin(info, "bar"));
		tb.accept(begin(info, "baz"));
		tb.accept(begin(info, "lorem"));
		tb.accept(begin(info, "ipsum"));
		tb.accept(end(info, "bar", "BarResult")); // synth end for ipsum , lorem and baz
		tb.accept(begin(info, "dolor"));
		tb.accept(end(info, "foo", "FooResult")); // synth end for dolor

		tb.stop();
		final Tracing result = tb.finish(CreateLocator.fromNode(info, info.ast));

		assertEquals("foo()", result.prop.name);
		assertEquals(2, result.dependencies.size());
		assertEquals("FooResult", result.result.asPlain());

		Tracing barDep = result.dependencies.get(0);
		assertEquals("bar()", barDep.prop.name);
		assertEquals("BarResult", barDep.result.asPlain());

		for (String next : new String[] { "baz()", "lorem()", "ipsum()"}) {
			assertEquals(1, barDep.dependencies.size());
			barDep = barDep.dependencies.get(0);
			assertEquals(next, barDep.prop.name);
			assertSame(TracingBuilder.NULL_RESULT, barDep.result);
		}
		assertEquals(0, barDep.dependencies.size());
		assertSame(TracingBuilder.NULL_RESULT, barDep.result);

		final Tracing dolorDep = result.dependencies.get(1);
		assertEquals("dolor()", dolorDep.prop.name);
		assertSame(TracingBuilder.NULL_RESULT, dolorDep.result);

	}
}
