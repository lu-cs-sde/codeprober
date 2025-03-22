package codeprober.util;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import codeprober.requesthandler.WorkspaceHandler;
import codeprober.server.ServerToClientEvent;
import codeprober.server.ServerToClientMessagePusher;

public class WorkspaceDirectoryMonitor extends DirectoryMonitor {

	private File workspaceRoot;
	final Set<String> changedWorkspacePaths = new HashSet<>();
	private ServerToClientMessagePusher msgPusher;

	public WorkspaceDirectoryMonitor(File workspaceRoot, ServerToClientMessagePusher msgPusher) {
		super(workspaceRoot);
		this.workspaceRoot = workspaceRoot;
		this.msgPusher = msgPusher;
	}

	@Override
	protected void onChangeDetected(Path filePath) {
		final Path rootPath = workspaceRoot.toPath();
		if (filePath.startsWith(rootPath)) {
			final File parent= filePath.toFile().getParentFile();
			if (parent != null && parent.getName().equals(WorkspaceHandler.METADATA_DIR_NAME)) {
				// Metadata file update, ignore it
				return;
			}
			final String relpath = rootPath.relativize(filePath).toString();
			synchronized (changedWorkspacePaths) {
				changedWorkspacePaths.add(relpath.replace(File.separatorChar, '/'));
			}
		}
	}

	@Override
	public void onChange() {
		synchronized (changedWorkspacePaths) {
			if (!changedWorkspacePaths.isEmpty()) {
				msgPusher.onChange(ServerToClientEvent.workspacePathChanged(new ArrayList<>(changedWorkspacePaths)));
				changedWorkspacePaths.clear();
			}
		}
	}
}
