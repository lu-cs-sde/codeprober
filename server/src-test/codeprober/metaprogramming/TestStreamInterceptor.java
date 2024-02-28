package codeprober.metaprogramming;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import codeprober.metaprogramming.StreamInterceptor.OtherThreadDataHandling;

public class TestStreamInterceptor {

	private static class TestSetup {
		public final ByteArrayOutputStream prev = new ByteArrayOutputStream();
		public final List<String> writtenLines = new ArrayList<>();
		public final StreamInterceptor si;

		public TestSetup(boolean autoPrintLinesToPrev, OtherThreadDataHandling otdh) {
			si = new StreamInterceptor(new PrintStream(prev, true), autoPrintLinesToPrev, otdh) {

				@Override
				protected void onLine(String line) {
					writtenLines.add(line);
				}
			};
		}

		public TestSetup() {
			// Match the default parameters of StreamInterceptor
			this(true, OtherThreadDataHandling.WRITE_TO_PREV);
		}

		public void writeDefaultInTwoThreads() throws InterruptedException {
			si.println("Initial");
			final CountDownLatch cdl = new CountDownLatch(1);
			new Thread(() -> {
				si.println("OtherThread");
				cdl.countDown();
			}).start();
			cdl.await();
			si.println("After");
			si.print("WithoutLn");
		}

		public void assertWrittenLines(String... lines) {
			assertArrayEquals(lines, writtenLines.toArray(new String[writtenLines.size()]));
		}

		public void assertPrevData(String dataAsStr) {
			assertEquals(dataAsStr, new String(prev.toByteArray()));

		}
	}

	@Test
	public void testDefaultBehvaior() throws InterruptedException {
		final TestSetup ts = new TestSetup();

		ts.writeDefaultInTwoThreads();

		// Main thread only captures main thread data ended by \n (i.e not 'WithoutLn')
		ts.assertWrittenLines("Initial", "After");
		// Other thread receives all data
		ts.assertPrevData("Initial\nOtherThread\nAfter\n");

		ts.si.consume();
		ts.assertWrittenLines("Initial", "After", "WithoutLn");
		ts.assertPrevData("Initial\nOtherThread\nAfter\nWithoutLn\n");
	}

	@Test
	public void testNoAutoPrint() throws InterruptedException {
		final TestSetup ts = new TestSetup(false, OtherThreadDataHandling.WRITE_TO_PREV);

		ts.writeDefaultInTwoThreads();

		ts.assertWrittenLines("Initial", "After");
		// Other thread only receives its own messages
		ts.assertPrevData("OtherThread\n");

		// Force consume/flush all messages
		ts.si.consume();
		ts.assertWrittenLines("Initial", "After", "WithoutLn");
		ts.assertPrevData("OtherThread\n");
	}

	@Test
	public void testMerge() throws InterruptedException {
		final TestSetup ts = new TestSetup(false, OtherThreadDataHandling.MERGE);

		ts.writeDefaultInTwoThreads();

		ts.assertWrittenLines("Initial", "OtherThread", "After");
		// Other thread receives nothing
		ts.assertPrevData("");

		ts.si.consume();
		ts.assertWrittenLines("Initial", "OtherThread", "After", "WithoutLn");
		ts.assertPrevData("");
	}

	@Test
	public void testStrangeWindowsIssue() throws IOException {
		final TestSetup ts = new TestSetup();
		// Regression test against messages ending with '\r'
		ts.si.write(new byte[] {'a', 'b', 'c', '\r'});
		ts.si.consume();
		ts.assertWrittenLines("abc");
		ts.assertPrevData("abc\n");
	}

	@Test
	public void testEmptyLines() {
		final TestSetup ts = new TestSetup();

		ts.si.println("Foo");
		ts.si.println("");
		ts.si.println("Bar");

		ts.assertWrittenLines("Foo", "", "Bar");
		ts.assertPrevData("Foo\n\nBar\n");
	}
}
