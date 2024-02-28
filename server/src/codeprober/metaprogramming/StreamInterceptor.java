package codeprober.metaprogramming;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public abstract class StreamInterceptor extends PrintStream {

	/**
	 * How to handle intercepted data in other threads.
	 */
	public enum OtherThreadDataHandling {
		/**
		 * Forward to the previous stream, usually a good option, unless you want to
		 * intercept all data in all threads.
		 */
		WRITE_TO_PREV,
		/**
		 * Accept/merge the incoming data into ourselves (pretend it comes from our
		 * thread). There is no guarantees about the ordering of the bytes being
		 * written, so some messages may look strange.
		 * <p>
		 * Also, this will result in {@link StreamInterceptor#onLine(String)} being
		 * called from multiple threads.
		 */
		MERGE;
	}

	private static class StreamInterceptorImpl extends OutputStream {

		private final ByteArrayOutputStream stdOut = new ByteArrayOutputStream();

		public final PrintStream prev;

		private Thread threadFilter = Thread.currentThread();

		Consumer<String> lineReader;

		private boolean autoPrintLinesToPrev;

		private final OtherThreadDataHandling otherThreadHandling;

		public StreamInterceptorImpl(PrintStream prev, boolean autoPrintLinesToPrev,
				OtherThreadDataHandling otherThreadHandling) {
			this.prev = prev;
			this.autoPrintLinesToPrev = autoPrintLinesToPrev;
			this.otherThreadHandling = otherThreadHandling;
		}

		@Override
		public void write(int b) throws IOException {
			switch (otherThreadHandling) {
			case WRITE_TO_PREV: // Fall through
			default: {
				if (Thread.currentThread() != threadFilter) {
					prev.write(b);
				} else if (b == '\n') {
					consume(true);
				} else {
					stdOut.write(b);
				}
				break;
			}
			case MERGE: {
				if (b == '\n') {
					consume(true);
				} else {
					synchronized (this) {
						stdOut.write(b);
					}
				}

				break;
			}
			}
		}

		private byte[] getDataToConsume(boolean forceLine) {
			if (stdOut.size() == 0 && !forceLine) {
				return null;
			}
			final byte[] barr = stdOut.toByteArray();
			stdOut.reset();
			return barr;
		}

		public void consume(boolean forceLine) {
			final byte[] barr;
			if (otherThreadHandling == OtherThreadDataHandling.MERGE) {
				synchronized (this) {
					barr = getDataToConsume(forceLine);
				}
			} else {
				barr = getDataToConsume(forceLine);
			}
			if (barr == null) {
				return;
			}

			String s;
			if (barr.length > 0 && barr[barr.length - 1] == '\r') {
				// Running on windows, remove carriage return
				s = new String(barr, 0, barr.length - 1, StandardCharsets.UTF_8);
			} else {
				s = new String(barr, StandardCharsets.UTF_8);
			}

			if (autoPrintLinesToPrev) {
				prev.println(s);
			}
			lineReader.accept(s);
		}
	}

	private final StreamInterceptorImpl dst;

	public StreamInterceptor(PrintStream prev) {
		this(prev, true);
	}

	public StreamInterceptor(PrintStream prev, boolean autoPrintLinesToPrev) {
		this(prev, autoPrintLinesToPrev, OtherThreadDataHandling.WRITE_TO_PREV);
	}

	public StreamInterceptor(PrintStream prev, boolean autoPrintLinesToPrev,
			OtherThreadDataHandling otherThreadHandling) {
		this(new StreamInterceptorImpl(prev, autoPrintLinesToPrev, otherThreadHandling));
	}

	private StreamInterceptor(StreamInterceptorImpl dst) {
		super(dst);
		this.dst = dst;
		dst.lineReader = s -> onLine(s);
	}

	public void consume() {
		super.flush();
		dst.consume(false);
	}

	public PrintStream getPrev() {
		return dst.prev;
	}

	protected abstract void onLine(String line);
}
