package addnum;

import java.util.Collection;

/**
 * Small implementation of a "fluent test"-style API. Each assertion method is
 * expected to throw an AssertionError or return the original
 * {@link TestInstance}. The {@link TestInstance} in turn implements
 * cpr_getOutput by returning "ok". Text probes using this helper should
 * therefore end with "=ok" or just "!" (which is syntax sugar for "!=false").
 * If an assertion fails, an error is thrown and CodeProber will show which step
 * failed.
 */
public class FluentTestHelper {

	public TestInstance of(Object o) {
		return new TestInstance(o);
	}

	public static class TestInstance {

		private final Object o;
		private TestInstance invertedParent;

		public TestInstance(Object o) {
			this(o, null);
		}

		private TestInstance(Object o, TestInstance invertedParent) {
			this.o = o;
			this.invertedParent = invertedParent;
		}

		public TestInstance not() {
			return new TestInstance(o, this);
		}

		public TestInstance contains(Object other) {
			if (o instanceof String) {
				return check(((String) o).contains(String.valueOf(other)));
			}
			if (o instanceof Collection<?>) {
				return check(((Collection<?>) o).contains(other));
			}
			throw new IllegalArgumentException(
					"Cannot check contains on " + (o == null ? "null" : o.getClass().getName()));
		}

		public TestInstance isEmpty() {
			if (o instanceof String) {
				return check(((String) o).isEmpty());
			}
			if (o instanceof Collection<?>) {
				return check(((Collection<?>) o).isEmpty());
			}
			throw new IllegalArgumentException(
					"Cannot check isEmpty on " + (o == null ? "null" : o.getClass().getName()));
		}

		public TestInstance isNull() {
			return check(o == null);
		}

		private TestInstance check(boolean b) {
			if (invertedParent != null) {
				invertedParent.check(!b);
			} else if (!b) {
				fail();
			}
			return this;
		}

		private void fail() {
			throw new AssertionError(String.format("Actual: %s", o));
		}

		public Object cpr_getOutput() {
			return "ok";
		}
	}
}
