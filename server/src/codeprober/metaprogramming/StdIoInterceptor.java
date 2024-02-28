package codeprober.metaprogramming;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import codeprober.CodeProber;
import codeprober.metaprogramming.StreamInterceptor.OtherThreadDataHandling;
import codeprober.protocol.data.RpcBodyLine;

public abstract class StdIoInterceptor {

	private StreamInterceptor out;
	private StreamInterceptor err;

	public StdIoInterceptor() {
		this(true);
	}

	public StdIoInterceptor(boolean autoPrintLinesToPrev) {
		this(autoPrintLinesToPrev, OtherThreadDataHandling.WRITE_TO_PREV);
	}

	public StdIoInterceptor(boolean autoPrintLinesToPrev, OtherThreadDataHandling otherThreadHandling) {
		out = new StreamInterceptor(System.out, autoPrintLinesToPrev, otherThreadHandling) {

			@Override
			protected void onLine(String line) {
				StdIoInterceptor.this.onLine(true, line);
			}
		};
		err = new StreamInterceptor(System.err, autoPrintLinesToPrev, otherThreadHandling) {

			@Override
			protected void onLine(String line) {
				StdIoInterceptor.this.onLine(false, line);
			}
		};
	}

	public static String tag = "";

	public static AtomicInteger installCount = new AtomicInteger();

	public void install() {
		CodeProber.flog("++intercept " + tag +", count: " + installCount.incrementAndGet());

		System.setOut(new PrintStream(out, true));
		System.setErr(new PrintStream(err, true));
	}

	public void flush() {
		out.consume();
		err.consume();
	}

	public void restore() {
		CodeProber.flog("--intercept " + tag +", count: " + installCount.decrementAndGet());
		System.setOut(out.getPrev());
		System.setErr(err.getPrev());
	}

	public abstract void onLine(boolean stdout, String line);

	public static void performLiveCaptured(BiConsumer<Boolean, String> onLine, Runnable body) {
		final StdIoInterceptor rootInterceptor = new StdIoInterceptor() {

			@Override
			public void onLine(boolean stdout, String line) {
				onLine.accept(stdout, line);
			}
		};
		rootInterceptor.install();
		try {
			body.run();
		} finally {
			rootInterceptor.flush();
			rootInterceptor.restore();
		}
	}

	public static <T> List<T> performCaptured(BiFunction<Boolean, String, T> lineParser, Runnable body) {
		final List<T> ret = new ArrayList<>();
		performLiveCaptured((stdout, line) -> {
			final T magicMsg = lineParser.apply(stdout, line);
			if (magicMsg != null) {
				ret.add(magicMsg);
			}
		}, body);
		return ret;
	}

	public static List<RpcBodyLine> performDefaultCapture(Runnable body) {
		return performCaptured(createDefaultLineEncoder(), body);
	}

	public static BiFunction<Boolean, String, RpcBodyLine> createDefaultLineEncoder() {
		return (stdout, line) -> {
			if (stdout) {
				return RpcBodyLine.fromStdout(line);
			}
			return RpcBodyLine.fromStderr(line);
		};
	}
}
