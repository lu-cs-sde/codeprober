package codeprober.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import codeprober.CodeProber;

public class PreferenceUtil {

	private static File prefsStorageLocation() {
		if ("true".equals(System.getenv("GRADLEPLUGIN"))) {
			// When running in gradle, try to store the prefs within the cache dir
			try {
				final File f = new File(CodeProber.class.getProtectionDomain().getCodeSource().getLocation().toURI())
						.getAbsoluteFile();
				if (f.getName().equals("codeprober.jar")) {
					final File parent = f.getParentFile();
					if (parent != null && new File(parent, "codeprober.jar.props").exists()) {
						// Very likely we are running from the cache dir. We should be safe to place our
						// preferences here.
						return new File(parent, "preferences.xml");
					}
				}
			} catch (URISyntaxException e) {

			}
		}
		return null;
	}

//	public static Properties getCodeProberPrefs() {
////		Preferences.systemRoot().
//		final File loc = prefsStorageLocation();
//		if (loc != null && loc.exists()) {
//			try (FileInputStream fis = new FileInputStream(loc)) {
//				Properties props = new Properties();
//				props.load(fis);
//				return props;
//			} catch (IOException | IllegalArgumentException e) {
//				System.err.println("Error when loading user props from " + loc);
//				e.printStackTrace();
//			}
//		}
//
//		return Preferences.userRoot().node("/org/codeprober/prefs");
//	}
//
//	public static Integer getMostRecentPort() {
//		final int ret = getCodeProberPrefs().getInt("most_recent_port", -1);
//		return ret != -1 ? ret : null;
//	}
//
//	public static void setMostRecentPort(int port) {
//		final Preferences p = getCodeProberPrefs();
//		p.putInt("most_recent_port", port);
//		try {
//			p.flush();
//		} catch (BackingStoreException e) {
//			System.err.println("Failed saving prefs");
//			e.printStackTrace();
//		}
//	}
}
