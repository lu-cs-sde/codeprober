package codeprober.util;

import java.io.InputStream;
import java.util.Properties;

public class VersionInfo {

	public final String revision;
	public final boolean clean;
	public final Integer buildTimeSeconds;

	public VersionInfo(String revision, boolean clean, Integer buildTimeSeconds) {
		this.revision = revision;
		this.clean = clean;
		this.buildTimeSeconds = buildTimeSeconds;
	}

	public VersionInfo() {
		this("UNKNOWN", false, null);
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

					String buildTimeStr = props.getProperty("Build-Time", null);

					sInstance = new VersionInfo( //
							props.getProperty("Git-Version", "UNKNOWN"), //
							props.getProperty("Git-Status", "DIRTY").equals("CLEAN"), //
							buildTimeStr == null ? null : Integer.parseInt(buildTimeStr) //
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
