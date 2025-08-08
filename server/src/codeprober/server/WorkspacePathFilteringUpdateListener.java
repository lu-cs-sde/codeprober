package codeprober.server;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import codeprober.requesthandler.WorkspaceHandler;

public class WorkspacePathFilteringUpdateListener {

	final Map<String, Long> knownWorkspacePathsLastModifieds = new ConcurrentHashMap<>();

	public void onWorkspacePathChanged(String path) {
		knownWorkspacePathsLastModifieds.put(path, getLastModified(path));
	}

	public boolean canIgnore(ServerToClientEvent event) {
		switch (event.type) {
		case WORKSPACE_PATH_CHANGED:
			for (String path : event.getWorkspacePathChanges()) {
				final Long known = knownWorkspacePathsLastModifieds.get(path);
				if (known == null) {
					return false;
				}
				final long actual = getLastModified(path);
				if (actual != known.longValue()) {
					return false;
				}
			}
			return true;

		default:
			return false;
		}
	}

	public Consumer<ServerToClientEvent> getFilteringChangeListener(Consumer<ServerToClientEvent> msgPusher) {
		return (msg) -> {
			if (!canIgnore(msg)) {
				msgPusher.accept(msg);
			}
		};
	}

	private static long getLastModified(String workspacePath) {
		final File ret = WorkspaceHandler.getDefault().getWorkspaceFile(workspacePath);
		return ret == null ? -1L : ret.lastModified();
	}
}
