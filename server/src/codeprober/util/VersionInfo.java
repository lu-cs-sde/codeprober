package codeprober.util;

import java.io.InputStream;
import java.util.Properties;

public class VersionInfo {

	public final String revision;
	public final boolean clean;

	public VersionInfo(String revision, boolean clean) {
		this.revision = revision;
		this.clean = clean;
	}

	public VersionInfo() {
		this("UNKNOWN", false);
	}

	public String toString() {
		return revision + (clean ? "" : " [DEV]");
	}

	private static VersionInfo sInstance;

	public static VersionInfo getInstance() {
		if (sInstance == null) {

			try (InputStream in = VersionInfo.class.getResourceAsStream("/cpr.properties")) {
				if (in == null) {
					System.out.println("No version info available");
					sInstance = new VersionInfo();
				} else {
					final Properties props = new Properties();
					props.load(in);
					
					sInstance = new VersionInfo( //
							props.getProperty("Git-Version", "UNKNOWN"), //
							props.getProperty("Git-Status", "DIRTY").equals("CLEAN") //
							);
				}
			} catch (Exception e) {
				System.err.println("Error when loading properties");
				e.printStackTrace();
				sInstance = new VersionInfo();
			}
		}
		return sInstance;
	}

}
