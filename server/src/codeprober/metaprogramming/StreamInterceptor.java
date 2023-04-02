package codeprober.metaprogramming;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public abstract class StreamInterceptor extends PrintStream {

	private static class StreamInterceptorImpl extends OutputStream {

		private final ByteArrayOutputStream stdOut = new ByteArrayOutputStream();

		public final PrintStream prev;

		private Thread threadFilter = Thread.currentThread();

		Consumer<String> lineReader;

		private boolean autoPrintLinesToPrev;

		public StreamInterceptorImpl(PrintStream prev, boolean autoPrintLinesToPrev) {
			this.prev = prev;
			this.autoPrintLinesToPrev = autoPrintLinesToPrev;
		}

		@Override
		public void write(int b) throws IOException {
			if (Thread.currentThread() != threadFilter) {
				prev.write(b);
				return;
			}
			if (b == '\n') {
				consume(true);
			} else {
				stdOut.write(b);
			}
		}

		public void consume(boolean forceLine) {
			if (stdOut.size() == 0 && !forceLine) {
				return;
			}
			final byte[] barr = stdOut.toByteArray();
			String s;
			if (barr.length > 0 && barr[barr.length - 1] == '\r') {
				// Running on windows, remove carriage return
				s = new String(barr, 0, barr.length - 1, StandardCharsets.UTF_8);
			} else {
				s = new String(barr, StandardCharsets.UTF_8);
			}
			stdOut.reset();

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
		this(new StreamInterceptorImpl(prev, autoPrintLinesToPrev));
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
