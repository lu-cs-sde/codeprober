package codeprober.server;

import org.json.JSONObject;

import codeprober.protocol.data.BackingFileUpdated;
import codeprober.protocol.data.Refresh;

public enum ServerToClientEvent {
	JAR_CHANGED, BACKING_FILE_CHANGED,;

	public JSONObject getUpdateMessage() {
		switch (this) {
		case JAR_CHANGED: {
			return new Refresh().toJSON();
		}
		case BACKING_FILE_CHANGED: {
			final String contents = BackingFileSettings.readBackingFileContents();
			if (contents == null) {
				System.out.printf("Cannot read backing file for %s event. Ignoring it\n", name());
				return null;
			}
			return new BackingFileUpdated(contents).toJSON();
		}
		default: {
			System.err.printf("Unknown %s type '%s'\n", getClass().getSimpleName(), name());
			return null;
		}
		}
	}
}
