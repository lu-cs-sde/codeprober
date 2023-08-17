package codeprober.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Event logger that logs interactions during a CodeProber session. Is opt-in,
 * and doesn't send the data anywhere. Instead, data is written to your file
 * system. Specify a logging dir with "-Dcpr.session_logger_dir=dirname".
 * <p>
 * Will save logs in "dirname/sessionid/logname.json", where sessionid is
 * randomly generated, and logname is an incrementing counter.
 * <p>
 * Logs are saved when {@link #AUTO_FLUSH_EVENT_COUNTER} events are met, or when
 * the user has been idle for {@link #AUTO_FLUSH_IDLE_TIME_MS}.
 */
public class SessionLogger {
	private static final int AUTO_FLUSH_EVENT_COUNTER = 128;
	private static final int AUTO_FLUSH_IDLE_TIME_MS = 10_000;
	// Stop after 128 MB
	private static long AUTO_STOP_DATA_THRESHOLD = 128 * 1024 * 1024;

	static {
		final String thresholdKey = "SESSION_LOGGER_DATA_LIMIT_KB";
		final String limitStr = System.getenv(thresholdKey);
		if (limitStr != null) {
			try {
				final int parsed = Integer.parseInt(limitStr);
				AUTO_STOP_DATA_THRESHOLD = parsed * 1024;
			} catch (NumberFormatException e) {
				System.err.println("Invalid '" + thresholdKey + "' value: " + limitStr);
				e.printStackTrace();
			}
		}
	}

	private static File getLoggingDir() {
		final String dirProp = System.getProperty("cpr.session_logger_dir");
		if (dirProp == null) {
			return null;
		}
		final File dir = new File(dirProp);
		if (!dir.exists() && !dir.mkdirs()) {
			System.out.println("Couldn't create logging dir '" + dirProp + "', perhaps permission problems?");
			return null;
		}
		return dir;
	}

	public static SessionLogger init() {
		final File dir = getLoggingDir();
		if (dir == null) {
			return null;
		}
		final String subHead = System.currentTimeMillis() + "_";
		String subTail = Base64.getEncoder().withoutPadding()
				.encodeToString(((Math.random() * Integer.MAX_VALUE) + "_" + (Math.random() * Integer.MAX_VALUE))
						.getBytes(StandardCharsets.UTF_8));
		if (subTail.length() > 8) {
			subTail = subTail.substring(subTail.length() - 8, subTail.length());
		}

		final File sub = new File(dir, subHead + subTail);
		if (!sub.mkdir()) {
			System.err.println(
					"Couldn't create logging sub-dir '" + sub.getAbsolutePath() + "', perhaps permission problems?");
			return null;
		}

		return new SessionLogger(sub);

	}

	private final File dir;
	private final List<JSONObject> pendingEvents = new ArrayList<>();

	private final AtomicInteger flushFileNameGen = new AtomicInteger(0);
	private long lastEventNanos = System.nanoTime();
	private AtomicBoolean stop = new AtomicBoolean(false);
	private final AutoFlusher flusher;
	private long numLoggedBytes = 0L;

	private SessionLogger(File dir) {
		this.dir = dir;
		this.flusher = new AutoFlusher();
		this.flusher.start();
	}

	public File getTargetDirectory() {
		return dir;
	}

	public void log(JSONObject data) {
		if (isStopped()) {
			return;
		}

		final JSONObject wrapper = new JSONObject();
		wrapper.put("t", System.currentTimeMillis());
		wrapper.put("d", data);

		synchronized (SessionLogger.this) {
			pendingEvents.add(wrapper);
			lastEventNanos = System.nanoTime();

			if (pendingEvents.size() >= AUTO_FLUSH_EVENT_COUNTER) {
				flush();
			} else {
				SessionLogger.this.notifyAll();
			}
		}
	}

	public boolean isStopped() {
		return stop.get();
	}

	public void stopLogger() {
		stop.set(true);
		flusher.interrupt();
	}

	private void flush() {
		final JSONArray arr;
		synchronized (SessionLogger.this) {
			if (pendingEvents.isEmpty()) {
				return;
			}

			arr = new JSONArray(pendingEvents);
			pendingEvents.clear();
			lastEventNanos = System.nanoTime();
		}
		final File dst = new File(dir, String.format("%04d.json", flushFileNameGen.getAndIncrement()));
		try {
			final byte[] data = arr.toString().getBytes(StandardCharsets.UTF_8);
			numLoggedBytes += data.length;
			Files.write(dst.toPath(), data, StandardOpenOption.CREATE_NEW);
		} catch (IOException e) {
			System.err.println("Error when flushing events to " + dst);
			e.printStackTrace();
		}
		if (numLoggedBytes >= AUTO_STOP_DATA_THRESHOLD) {
			System.out.println("Stopping SessionLogger, amount of data written has reached threshold.");
			stopLogger();
		}
	}

	private class AutoFlusher extends Thread {

		private long getTimeSinceLastEventMs() {
			return (System.nanoTime() - lastEventNanos) / 1_000_000;
		}

		public void run() {
			while (!isStopped()) {
				synchronized (SessionLogger.this) {
					try {
						if (pendingEvents.isEmpty()) {
							SessionLogger.this.wait();
						} else {
							SessionLogger.this.wait(Math.max(1, AUTO_FLUSH_IDLE_TIME_MS - getTimeSinceLastEventMs()));
						}
					} catch (InterruptedException e) {
						continue;
					}
					if (!pendingEvents.isEmpty() && getTimeSinceLastEventMs() >= AUTO_FLUSH_EVENT_COUNTER) {
						flush();
					}
				}

			}
		}
	}
}
