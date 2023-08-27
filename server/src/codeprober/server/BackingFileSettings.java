package codeprober.server;

import java.io.File;

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
}
