package pasta;

import java.io.PrintStream;

public abstract class StdIoInterceptor {

	private StreamInterceptor out;
	private StreamInterceptor err;

	public StdIoInterceptor() {
		out = new StreamInterceptor(System.out) {

			@Override
			protected void onLine(String line) {
				StdIoInterceptor.this.onLine(true, line);
			}
		};
		err = new StreamInterceptor(System.err) {

			@Override
			protected void onLine(String line) {
				StdIoInterceptor.this.onLine(false, line);
			}
		};
	}

	public void install() {
		System.setOut(new PrintStream(out, true));
		System.setErr(new PrintStream(err, true));
	}

	public void flush() {
		out.consume(false);
		err.consume(false);
	}

	public void restore() {
		System.setOut(out.prev);
		System.setErr(err.prev);
	}

	public abstract void onLine(boolean stdout, String line);
}
