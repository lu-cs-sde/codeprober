package codeprober.metaprogramming;

import java.io.PrintStream;
import java.util.function.BiFunction;

import org.json.JSONArray;
import org.json.JSONObject;

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
	
	
	public static JSONArray performCaptured(BiFunction<Boolean, String, JSONObject> lineParser, Runnable body) {
		final JSONArray ret = new JSONArray();
		StdIoInterceptor rootInterceptor = new StdIoInterceptor() {

			@Override
			public void onLine(boolean stdout, String line) {
				final JSONObject magicMsg = lineParser.apply(stdout, line);
				if (magicMsg != null) {
					ret.put(magicMsg);
				}
			}
		};
		rootInterceptor.install();
		try {
			body.run();
		} finally {
			rootInterceptor.flush();
			rootInterceptor.restore();
		}
		return ret;
	}
	
	public static JSONArray performDefaultCapture(Runnable body) {
		return performCaptured((stdout, line) -> {
			JSONObject fmt = new JSONObject();
			fmt.put("type", stdout ? "stdout" : "stderr");
			fmt.put("value", line);
			return fmt;
		}, body);
	}
}
