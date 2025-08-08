package codeprober.server;

import java.util.List;

import org.json.JSONObject;

import codeprober.protocol.data.BackingFileUpdated;
import codeprober.protocol.data.Refresh;
import codeprober.protocol.data.WorkspacePathsUpdated;

public class ServerToClientEvent {

	public enum Type {
		JAR_CHANGED, BACKING_FILE_CHANGED, WORKSPACE_PATH_CHANGED, RAW_MESSAGE;
	}

	public static ServerToClientEvent JAR_CHANGED = new ServerToClientEvent(Type.JAR_CHANGED);
	public static ServerToClientEvent BACKING_FILE_CHANGED = new ServerToClientEvent(Type.BACKING_FILE_CHANGED);

	public final Type type;
	private Object extras;

	private ServerToClientEvent(Type type) {
		this.type = type;
		extras = null;
	}

	public List<String> getWorkspacePathChanges() {
		if (type != Type.WORKSPACE_PATH_CHANGED) {
			throw new UnsupportedOperationException(
					"Cannot access workspace path changes on a non-workspace-path-change event");
		}

		@SuppressWarnings("unchecked")
		List<String> cast = (List<String>) extras;
		return cast;
	}

	public static ServerToClientEvent workspacePathChanged(List<String> path) {
		final ServerToClientEvent ret = new ServerToClientEvent(Type.WORKSPACE_PATH_CHANGED);
		ret.extras = path;
		return ret;
	}

	public static ServerToClientEvent rawMessage(JSONObject message) {
		final ServerToClientEvent ret = new ServerToClientEvent(Type.RAW_MESSAGE);
		ret.extras = message;
		return ret;
	}

	public String toString() {
		return String.format("%s: %s", String.valueOf(type), String.valueOf(extras));
	}

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
			return new WorkspacePathsUpdated(getWorkspacePathChanges()).toJSON();
		}

		case RAW_MESSAGE: {
			return (JSONObject) extras;
		}
		default: {
			System.err.printf("Unknown %s type '%s'\n", getClass().getSimpleName(), type.name());
			return null;
		}
		}
	}
}
