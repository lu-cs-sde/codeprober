package codeprober.server;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import codeprober.util.FileMonitor;

public class BackingFileSettings {

	public static File getRealFileToBeUsedInRequests() {
		final String path = System.getProperty("cpr.backing_file");
		if (path == null) {
			return null;
		}
		final File file = new File(path).getAbsoluteFile();
		final File parent = file.getParentFile();
		if (parent == null || !parent.exists()) {
			System.err.println("The directory for " + path + " does not exist");
			System.exit(1);
		}
		return file;
	}

	public static String readBackingFileContents() {
		final File backingFile = getRealFileToBeUsedInRequests();
		if (backingFile == null || !backingFile.exists()) {
			System.out.println("No backing file - cannot get backing file contents");
			return null;
		}
		try {
			return Files.readAllLines(backingFile.toPath()).stream().collect(Collectors.joining("\n"));
		} catch (IOException e) {
			System.err.println(
					"Failed reading state of backing file " + backingFile + ", perhaps there are permission problems?");
			e.printStackTrace();
			return null;
		}

	}

	public static void write(String inputText) throws IOException {
		final File backingFile = getRealFileToBeUsedInRequests();
		if (backingFile == null) {
			System.out.println("No backing file configured, ignoring backing file write");
			return;
		}
		final byte[] newBytes = inputText.getBytes(StandardCharsets.UTF_8);
		if (newBytes.length != backingFile.length()
				|| !Arrays.equals(Files.readAllBytes(backingFile.toPath()), newBytes)) {
			ignoreChanges.set(true);
			try {
				Files.write(backingFile.toPath(), newBytes, StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING);
			} finally {
				ignoreChanges.set(false);
			}
			lastModifiedThreshold.set(backingFile.lastModified() + 1L);
		}
	}

	private static AtomicBoolean ignoreChanges = new AtomicBoolean();
	private static AtomicLong lastModifiedThreshold = new AtomicLong();

	public static void monitorBackingFileChanges(final Runnable callback) {
		final File backingFile = getRealFileToBeUsedInRequests();
		if (backingFile == null) {
			System.out.println("No backing file configured, not going to monitor it for changes");
			return;
		}
		new FileMonitor(backingFile) {
			public void onChange() {
				if (ignoreChanges.get()) {
					return;
				}
				final long lm = backingFile.lastModified();
				if (lm < lastModifiedThreshold.get()) {
					return;
				}
				callback.run();
			};
		}.start();

	}
}
