package codeprober.util;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.json.JSONObject;

import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.metaprogramming.StreamInterceptor.OtherThreadDataHandling;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.data.BlessFileReq;
import codeprober.protocol.data.BlessFileRes;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.protocol.data.WorkspaceFile;
import codeprober.requesthandler.BlessFileMode;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;
import codeprober.textprobe.TextProbeEnvironment;

public class BlessWorkspace {

	private final JsonRequestHandler dreqHandler;
	private final WorkspaceHandler wsHandler;
	private int numUpdatedProbes;
	private int numUpdatedFiles;
	private final BlessFileMode mode;
	private boolean anyError;

	public BlessWorkspace(JsonRequestHandler dreqHandler, WorkspaceHandler wsHandler, BlessFileMode mode) {
		this.dreqHandler = dreqHandler;
		this.wsHandler = wsHandler;
		this.mode = mode;
	}

	public void run() {
		final PrintStream out = System.out;
		final Consumer<String> println = line -> out.println(line);
		StdIoInterceptor interceptor = null;
		if (!Util.verbose) {
			// Prevent messages during test runs
			interceptor = new StdIoInterceptor(false, OtherThreadDataHandling.MERGE) {
				@Override
				public void onLine(boolean stdout, String line) {
					// Noop
				}
			};
			interceptor.install();
		}
		try {
			runDirectory(null, println);
		} finally {
			if (interceptor != null) {
				interceptor.restore();
			}
		}
		System.out.printf("%s %d probe(s) in %d file(s)%n", mode == BlessFileMode.DRY_RUN ? "Would update" : "Updated",
				numUpdatedProbes, numUpdatedFiles);
		if (anyError) {
			System.out.println("Files with errors (❌) were skipped. Please fix the errors and re-run the bless command");
		}
	}

	private void runDirectory(String workspacePath, Consumer<String> println) {
		final ListWorkspaceDirectoryRes listRes = wsHandler
				.handleListWorkspaceDirectory(new ListWorkspaceDirectoryReq(workspacePath));
		if (listRes.entries == null) {
			System.err.println("Invalid path in workspace: " + workspacePath);
			return;
		}

		final String parentPath = workspacePath == null ? "" : (workspacePath + "/");
		for (WorkspaceEntry e : listRes.entries) {
			switch (e.type) {
			case directory: {
				runDirectory(parentPath + e.asDirectory(), println);
				break;
			}
			case file: {
				final WorkspaceFile wsFile = e.asFile();
				if (wsFile.readOnly != null && wsFile.readOnly) {
					// Ignore
					continue;
				}
				final String fullPath = parentPath + wsFile.name;

				final Runnable runFile = () -> {
					// TODO add flag to make expected values get printed too (expected + actual)

					final BlessFileReq req = new BlessFileReq(TextProbeEnvironment.createParsingRequestData(fullPath),
							mode);
					final JSONObject resJson = dreqHandler.handleRequest(new ClientRequest(req.toJSON(), u -> {
					}, new AtomicBoolean(true), p -> {
					}));

					final BlessFileRes res = BlessFileRes.fromJSON(resJson);
					if (res.numUpdatedProbes == null) {
						println.accept("  ❌ " + fullPath);
						for (String line : (res.result != null ? res.result : "Unknown error processing file")
								.split("\n")) {
							println.accept("     " + line);
						}
						anyError = true;
						return;
					}
					if (res.numUpdatedProbes == 0) {
						return;
					}

					numUpdatedFiles++;
					numUpdatedProbes += res.numUpdatedProbes;
					if (mode == BlessFileMode.UPDATE_IN_PLACE) {
						println.accept("  ✅ " + fullPath + " - " + res.numUpdatedProbes + " updates");
					} else {
						println.accept("  ✅ " + fullPath);
						for (String line : res.result.split("\n")) {
							println.accept("     " + line);
						}
					}
				};
				runFile.run();
			}
			}
		}
	}
}
