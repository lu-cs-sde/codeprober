package codeprober.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Event logger that logs interactions during a CodeProber session. Is opt-in,
 * and doesn't send the data anywhere. Instead, data is written to your file
 * system. Specify a logging path with "-Dcpr.session_logger_dir=path".
 * <p>
 * If path does *not* end with ".json", then SessionLogger will save logs in
 * "path/sessionid/logname.json", where sessionid is randomly generated, and
 * logname is an incrementing counter.
 * <p>
 * If path does end with ".json", then SessionLogger will append logs to a file
 * with the specified path. This can be used to create a persistent log file
 * across multiple sessions. Note however that this file will not be valid JSON,
 * it will be a concatenation of several JSON arrays.
 * <p>
 * Logs are saved when {@link #AUTO_FLUSH_EVENT_COUNTER} events are met, or when
 * the user has been idle for {@link #AUTO_FLUSH_IDLE_TIME_MS}.
 */
public class SessionLogger {
	private static final int AUTO_FLUSH_EVENT_COUNTER = 128;
	private static final int AUTO_FLUSH_IDLE_TIME_MS = 10_000;
	private static final String PREFS_KEY_PERSISTENT_LOG_FILE = "cpr-session-log-path";
	private static final boolean LOG_TO_SINGLE_FILE_BY_DEFAULT = false;
	// Stop after 32 MB
	private static long AUTO_STOP_DATA_THRESHOLD = 32 * 1024 * 1024;

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

	private static File getLoggingPath() {
		String persistentLoggingFile = null;
		if (LOG_TO_SINGLE_FILE_BY_DEFAULT) {
			try {
				final Preferences prefs = Preferences.userRoot();
				persistentLoggingFile = prefs.get(PREFS_KEY_PERSISTENT_LOG_FILE, null);
				if (persistentLoggingFile != null
						&& (!persistentLoggingFile.startsWith("logs/L") || !persistentLoggingFile.endsWith(".json"))) {
					System.out.printf("Unexpected persistent log file from preferences, discarding it. Old name: '%s'\n",
							persistentLoggingFile);
					persistentLoggingFile = null;
				}
				if (persistentLoggingFile != null) {
					final File sizeCheck = new File(persistentLoggingFile);
					if (sizeCheck.exists() && sizeCheck.length() > AUTO_STOP_DATA_THRESHOLD) {
						// Quite unlikely, but if somebody uses CodeProber for a very long time on the
						// same machine, with logging enabled, it could create a massive log file.
						// Big files could be for example be rejected by git hosting providers, which is
						// a problem. Solve the problem by generating a new log name.
						System.out.printf(
								"Persistent log file '%s' has grown quite large, leaving it as-is and generating a new log name\n",
								persistentLoggingFile);
					}
				}
				if (persistentLoggingFile == null) {
					persistentLoggingFile = String.format("logs/L%s.json", generateRandomCharacters());
					prefs.put(PREFS_KEY_PERSISTENT_LOG_FILE, persistentLoggingFile);
					prefs.flush();
				}
			} catch (SecurityException | BackingStoreException e) {
				System.out.println("Error reading/writing system preferences");
				e.printStackTrace();
			}
		}
		final String pathProp = System.getProperty("cpr.session_logger_dir", persistentLoggingFile);
		if (pathProp == null || pathProp.equals("")) {
			return null;
		}
		File dir = new File(pathProp);
		final File ret = dir;
		if (pathProp.endsWith(".json")) {
			// It is not a directory, it is a file
			dir = dir.getParentFile();
			if (dir == null) {
				// Root of file system, exit
				return null;
			}
		}
		if (!dir.exists() && !dir.mkdirs()) {
			System.out.println("Couldn't create logging dir '" + pathProp + "', perhaps permission problems?");
			return null;
		}
		return ret;
	}

	private static String generateRandomCharacters() {
		return generateRandomCharacters(8);
	}

	private static String generateRandomCharacters(int limit) {
		String ret = Base64.getEncoder().withoutPadding()
				.encodeToString(((Math.random() * Integer.MAX_VALUE) + "_" + (Math.random() * Integer.MAX_VALUE))
						.getBytes(StandardCharsets.UTF_8));
		if (ret.length() > limit) {
			ret = ret.substring(ret.length() - limit, ret.length());
		}
		return ret;
	}

	public static SessionLogger init() {
		final File path = getLoggingPath();
		if (path == null) {
			return null;
		}
		if (path.getName().endsWith(".json")) {
			return new SessionLogger(path);
		}
		final File sub = new File(path,
				String.format("%s_%s", new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.ENGLISH).format(new Date()),
						generateRandomCharacters()));
		if (!sub.mkdir()) {
			System.err.println(
					"Couldn't create logging sub-dir '" + sub.getAbsolutePath() + "', perhaps permission problems?");
			return null;
		}

		return new SessionLogger(sub);

	}

	private final File targetPath;
	private final List<JSONObject> pendingEvents = new ArrayList<>();

	private final AtomicInteger flushFileNameGen = new AtomicInteger(0);
	private long lastEventNanos = System.nanoTime();
	private AtomicBoolean stop = new AtomicBoolean(false);
	private final AutoFlusher flusher;
	private long numLoggedBytes = 0L;
	private String sessionId;

	private SessionLogger(File dirOrFile) {
		this.targetPath = dirOrFile;
		this.sessionId = generateRandomCharacters(4);
		this.flusher = new AutoFlusher();
		this.flusher.start();
	}

	public File getTargetPath() {
		return targetPath;
	}

	public void log(JSONObject data) {
		if (isStopped()) {
			return;
		}

		final JSONObject wrapper = new JSONObject();
		wrapper.put("t", System.currentTimeMillis());
		wrapper.put("s", sessionId);
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
		final File dst;
		final OpenOption[] writeOptions;
		if (targetPath.getName().endsWith(".json")) {
			// Just append to the file
			dst = targetPath;
			writeOptions = new StandardOpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.APPEND };
		} else {
			// Create new file
			dst = new File(targetPath, String.format("%04d.json", flushFileNameGen.getAndIncrement()));
			writeOptions = new StandardOpenOption[] { StandardOpenOption.CREATE_NEW };
		}
		if (!dst.getParentFile().exists()) {
			// We _could_ recover here by recreating the directory.
			// However, the safe choice is to interpret the deletion of the dir as a signal
			// to stop.
			// Otherwise the user may get very annoyed that the directory keeps popping up.
			System.out.println("Target directory for SessionLogger has disappeared, stopping.");
			stopLogger();
			return;
		}
		try {
			final byte[] data = (arr.toString() + "\n").getBytes(StandardCharsets.UTF_8);
			numLoggedBytes += data.length;
			Files.write(dst.toPath(), data, writeOptions);
		} catch (IOException e) {
			System.err.println("SessionLogger: Error when flushing events to " + dst + ". Stopping.");
			e.printStackTrace();
			// Again, here we could just ignore the error and keep on going
			// But let's be cautious and just stop, instead of constantly bombarding the
			// file system with faulty writes.
			stopLogger();
			return;
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
