package codeprober.server;

import java.util.List;

import org.json.JSONObject;

import codeprober.protocol.data.BackingFileUpdated;
import codeprober.protocol.data.Refresh;
import codeprober.protocol.data.WorkspacePathsUpdated;

public class ServerToClientEvent {

	public enum Type {
		JAR_CHANGED, BACKING_FILE_CHANGED, WORKSPACE_PATH_CHANGED;
	}

	public static ServerToClientEvent JAR_CHANGED = new ServerToClientEvent(Type.JAR_CHANGED);
	public static ServerToClientEvent BACKING_FILE_CHANGED = new ServerToClientEvent(Type.BACKING_FILE_CHANGED);

	public final Type type;
	private Object extras;

	private ServerToClientEvent(Type type) {
		this.type = type;
		extras = null;
	}

	public static ServerToClientEvent workspacePathChanged(List<String> path) {
		final ServerToClientEvent ret = new ServerToClientEvent(Type.WORKSPACE_PATH_CHANGED);
		ret.extras = path;
		return ret;
	}

	@SuppressWarnings("unchecked")
	public JSONObject getUpdateMessage() {
		switch (type) {
		case JAR_CHANGED: {
			return new Refresh().toJSON();
		}
		case BACKING_FILE_CHANGED: {
			final String contents = BackingFileSettings.readBackingFileContents();
			if (contents == null) {
				System.out.printf("Cannot read backing file for %s event. Ignoring it\n", type.name());
				return null;
			}
			return new BackingFileUpdated(contents).toJSON();
		}
		case WORKSPACE_PATH_CHANGED: {
			return new WorkspacePathsUpdated((List<String>)extras).toJSON();

		}
		default: {
			System.err.printf("Unknown %s type '%s'\n", getClass().getSimpleName(), type.name());
			return null;
		}
		}
	}
}
