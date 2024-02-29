package codeprober.toolglue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class TestAstStateResetter {

	private UnderlyingTool createTool(Object parseResult) {
		return (args) -> new ParseResult(parseResult);
	}

	@Test
	public void testWorksWithFailedParses() {
		final AstStateResetter asr = new AstStateResetter(createTool(null));
		final ParseResult result = asr.parse(new String[] {});

		assertNull(result.rootNode);
	}

	public void testWorksIfStateDoesNotExist() {
		final Object node = new Object() {
		};
		final AstStateResetter asr = new AstStateResetter(createTool(node));
		final ParseResult result = asr.parse(new String[] {});

		assertSame(node, result.rootNode);

	}

	@Test
	public void testWorksIfStateReturnsDifferentData() {
		final AtomicBoolean stateWasCalled = new AtomicBoolean();
		final Object node = new Object() {
			@SuppressWarnings("unused")
			public String state() {
				stateWasCalled.set(true);
				return "foo";
			}
		};
		final AstStateResetter asr = new AstStateResetter(createTool(node));
		final ParseResult result = asr.parse(new String[] {});

		assertSame(node, result.rootNode);
		assertFalse(stateWasCalled.get());

	}

	@Test
	public void testDoesCallReset() {
		final ASTState state = new ASTState();
		final Object node = new Object() {
			@SuppressWarnings("unused")
			public ASTState state() {
				return state;
			}
		};
		final AstStateResetter asr = new AstStateResetter(createTool(node));
		final ParseResult result = asr.parse(new String[] {});

		assertSame(node, result.rootNode);
		assertEquals(1, state.resetCallCounter);
	}

	public static class ASTState {
		public int resetCallCounter = 0;

		public void reset() {
			resetCallCounter++;
		}
	}
}
