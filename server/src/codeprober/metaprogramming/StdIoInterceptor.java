package codeprober.metaprogramming;

import java.io.PrintStream;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.json.JSONArray;
import org.json.JSONObject;

public abstract class StdIoInterceptor {

	private StreamInterceptor out;
	private StreamInterceptor err;

	public StdIoInterceptor() {
		this(true);
	}

	public StdIoInterceptor(boolean autoPrintLinesToPrev) {
		out = new StreamInterceptor(System.out, autoPrintLinesToPrev) {

			@Override
			protected void onLine(String line) {
				StdIoInterceptor.this.onLine(true, line);
			}
		};
		err = new StreamInterceptor(System.err, autoPrintLinesToPrev) {

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
		out.consume();
		err.consume();
	}

	public void restore() {
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

	public static JSONArray performCaptured(BiFunction<Boolean, String, JSONObject> lineParser, Runnable body) {
		final JSONArray ret = new JSONArray();
		performLiveCaptured((stdout, line) -> {
			final JSONObject magicMsg = lineParser.apply(stdout, line);
			if (magicMsg != null) {
				ret.put(magicMsg);
			}
		}, body);
		return ret;
	}

	public static JSONArray performDefaultCapture(Runnable body) {
		return performCaptured(createDefaultLineEncoder(), body);
	}

	public static BiFunction<Boolean, String, JSONObject> createDefaultLineEncoder() {
		return (stdout, line) -> {
			JSONObject fmt = new JSONObject();
			fmt.put("type", stdout ? "stdout" : "stderr");
			fmt.put("value", line);
			return fmt;
		};
	}
}
