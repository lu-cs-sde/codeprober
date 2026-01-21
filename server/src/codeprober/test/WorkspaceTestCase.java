package codeprober.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import codeprober.DefaultRequestHandler;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.requesthandler.LazyParser;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;
import codeprober.textprobe.TextProbeEnvironment;
import codeprober.textprobe.TextProbeEnvironment.VariableLoadStatus;
import codeprober.textprobe.Parser;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Document;
import codeprober.textprobe.ast.Probe;
import codeprober.textprobe.ast.Probe.Type;
import codeprober.textprobe.ast.Query;
import codeprober.textprobe.ast.VarDecl;
import codeprober.toolglue.UnderlyingTool;

public class WorkspaceTestCase implements Comparable<WorkspaceTestCase> {

	private final TextProbeEnvironment env;
	private final String srcFilePath;
	private final Query assertion;

	public WorkspaceTestCase(TextProbeEnvironment env, String srcFilePath, Query assertion) {
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
				+ (assertion == null ? "Variable load checking" : ((assertion.start.line) + " -> " + assertion.pp()));
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
		return Integer.compare(assertion.start.line, o.assertion.start.line);
	}

	public void doAssert(boolean expectPass) {
		if (env.getVariableStatus() == VariableLoadStatus.NONE) {
			final Set<String> staticProblems = env.document.problems();
			if (!staticProblems.isEmpty()) {
				if (expectPass) {
					fail("Document errors: " + staticProblems);
				} else {
					// OK
					return;
				}
			}
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

		if (!(assertion.assertion.isPresent())) {
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

	public static void listWorkspaceFilePaths(WorkspaceHandler workspaceHandler, String prefix,
			Consumer<String> onFoundPath) {
		final ListWorkspaceDirectoryRes res = workspaceHandler
				.handleListWorkspaceDirectory(new ListWorkspaceDirectoryReq(prefix));
		if (res.entries == null) {
			return;
		}
		for (WorkspaceEntry entry : res.entries) {
			if (entry.isDirectory()) {
				final String fullPath = (prefix != null ? (prefix + "/") : "") + entry.asDirectory();
				listWorkspaceFilePaths(workspaceHandler, fullPath, onFoundPath);
			} else {
				final String fullPath = (prefix != null ? (prefix + "/") : "") + entry.asFile().name;
				onFoundPath.accept(fullPath);
			}
		}
	}

	public static List<WorkspaceTestCase> listTextProbesInWorkspace(UnderlyingTool tool, File dir) throws IOException {
		final WorkspaceHandler wsh = new WorkspaceHandler(dir);
		final JsonRequestHandler requestHandler = new DefaultRequestHandler(tool, wsh);
		final List<WorkspaceTestCase> ret = new ArrayList<>();
		listWorkspaceFilePaths(wsh, null, fullPath -> {
			final ParsingSource psrc = ParsingSource.fromWorkspacePath(fullPath);

			final Document doc = Parser.parse(LazyParser.extractText(psrc, wsh), '[', ']');
			final TextProbeEnvironment env = new TextProbeEnvironment(requestHandler, wsh, psrc, doc, null, false);
			if (!doc.problems().isEmpty()) {
				// There are static errors. Add dummy case
				// for reporting them
				ret.add(new WorkspaceTestCase(env, fullPath, null));
			} else {
				boolean anyQuery = false;
				boolean anyVar = false;
				for (Container c : doc.containers) {
					Probe probe = c.probe();
					if (probe == null) {
						return;
					}
					switch (probe.type) {
					case QUERY:
						anyQuery = true;
						ret.add(new WorkspaceTestCase(env, fullPath, probe.asQuery()));
						break;
					case VARDECL:
						anyVar = true;
						break;
					default:
						System.err.print("Unknown probe type " + probe.type);
					}
				}
				if (!anyQuery && anyVar) {
					// No explicit queries, but we still want to evaluate varDecl's.
					// Create case with null query
					ret.add(new WorkspaceTestCase(env, fullPath, null));
				}
			}
		});
		return ret;
	}
}
