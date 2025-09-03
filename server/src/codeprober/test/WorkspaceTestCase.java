package codeprober.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import codeprober.DefaultRequestHandler;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;
import codeprober.textprobe.TextAssertionMatch;
import codeprober.textprobe.TextProbeEnvironment;
import codeprober.textprobe.TextProbeEnvironment.VariableLoadStatus;
import codeprober.toolglue.UnderlyingTool;

public class WorkspaceTestCase implements Comparable<WorkspaceTestCase> {

	private final TextProbeEnvironment env;
	private final String srcFilePath;
	private final TextAssertionMatch assertion;

	public WorkspaceTestCase(TextProbeEnvironment env, String srcFilePath, TextAssertionMatch assertion) {
		this.env = env;
		this.srcFilePath = srcFilePath;
		this.assertion = assertion;
	}

	public String getSrcFilePath() {
		return srcFilePath;
	}

	// name() is used by JUnit to label the test case
	public String name() {
		return srcFilePath + ":"
				+ (assertion == null ? "Variable load checking" : ((assertion.lineIdx + 1) + " -> " + assertion.full));
	}

	@Override
	public int compareTo(WorkspaceTestCase o) {
		final int cmp = srcFilePath.compareTo(o.srcFilePath);
		if (cmp != 0) {
			return cmp;
		}
		if ((assertion == null) != (o.assertion == null)) {
			return assertion == null ? -1 : 1;
		}
		if (assertion == null) {
			return 0;
		}
		return Integer.compare(assertion.lineIdx, o.assertion.lineIdx);
	}

	public void doAssert(boolean expectPass) {
		if (env.getVariableStatus() == VariableLoadStatus.NONE) {
			env.loadVariables();
		}
		if (env.getVariableStatus() == VariableLoadStatus.LOAD_ERR) {
			if (expectPass) {
				fail("Failed loading variables: " + env.errMsgs);
			}
			return;
		}
		if (assertion == null) {
			if (!expectPass) {
				fail("Expected variable loading to fail");
			}
			return;
		}

		final List<RpcBodyLine> result = env.evaluateQuery(assertion);
		if (result == null) {
			if (expectPass) {
				fail(env.errMsgs.isEmpty() ? "No matching nodes" : env.errMsgs.toString());
			}
			return;
		}

		final boolean ok = env.evaluateComparison(assertion, result);
		if (expectPass) {
			assertTrue(env.errMsgs.toString(), ok);
		} else {
			assertFalse(env.errMsgs.toString(), ok);
		}
	}

	public void assertPass() {
		doAssert(true);
	}

	public void assertFail() {
		doAssert(false);
	}

	@Override
	public String toString() {
		return name();
	}

	private static void listAllWorkspaceFiles(JsonRequestHandler requestHandler, WorkspaceHandler workspaceHandler,
			String prefix, List<WorkspaceTestCase> out) {
		final ListWorkspaceDirectoryRes res = workspaceHandler
				.handleListWorkspaceDirectory(new ListWorkspaceDirectoryReq(prefix));
		if (res.entries == null) {
			return;
		}
		for (WorkspaceEntry entry : res.entries) {
			if (entry.isDirectory()) {
				final String fullPath = (prefix != null ? (prefix + "/") : "") + entry.asDirectory();
				listAllWorkspaceFiles(requestHandler, workspaceHandler, fullPath, out);
			} else {
				final String fullPath = (prefix != null ? (prefix + "/") : "") + entry.asFile();
				final TextProbeEnvironment env = new TextProbeEnvironment(requestHandler, workspaceHandler,
						ParsingSource.fromWorkspacePath(fullPath), null);

				if (env.parsedFile.assertions.isEmpty() && env.parsedFile.assignments.isEmpty()) {
					continue;
				}
				if (env.parsedFile.assertions.isEmpty()) {
					out.add(new WorkspaceTestCase(env, fullPath, null));
				} else {
					for (TextAssertionMatch tam : env.parsedFile.assertions) {
						out.add(new WorkspaceTestCase(env, fullPath, tam));
					}
				}
			}
		}
	}

	public static List<WorkspaceTestCase> listTextProbesInWorkspace(UnderlyingTool tool, File dir) throws IOException {
		final WorkspaceHandler wsh = new WorkspaceHandler(dir);
		final JsonRequestHandler requestHandler = new DefaultRequestHandler(tool, wsh);
		final List<WorkspaceTestCase> ret = new ArrayList<>();
		listAllWorkspaceFiles(requestHandler, wsh, null, ret);
		return ret;
	}
}
