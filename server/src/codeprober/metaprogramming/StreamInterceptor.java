package codeprober.metaprogramming;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public abstract class StreamInterceptor extends OutputStream {

	private final ByteArrayOutputStream stdOut = new ByteArrayOutputStream();

	public final PrintStream prev;

	private Thread threadFilter = Thread.currentThread();

	public StreamInterceptor(PrintStream prev) {
		this.prev = prev;
	}

	@Override
	public void write(int b) throws IOException {
		if (Thread.currentThread() != threadFilter) {
			prev.write(b);
			return;
		}
		if (b == (int) '\n') {
			consume(true);
		} else {
			stdOut.write(b);
		}
	}

	public void consume(boolean forceLine) {
		if (stdOut.size() == 0 && !forceLine) {
			return;
		}
		String s = new String(stdOut.toByteArray(), StandardCharsets.UTF_8);
		stdOut.reset();

		prev.println(s);
		onLine(s);
	}

	protected abstract void onLine(String line);
}
