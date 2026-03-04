package codeprober.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import codeprober.DefaultRequestHandler;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.requesthandler.LazyParser;
import codeprober.requesthandler.LazyParser.ParsedAst;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.textprobe.Parser;
import codeprober.textprobe.TextProbeEnvironment;
import codeprober.textprobe.TextProbeEnvironment.QueryResult;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Document;
import codeprober.textprobe.ast.Probe;
import codeprober.textprobe.ast.Probe.Type;
import codeprober.textprobe.ast.Query;
import codeprober.toolglue.UnderlyingTool;

public class WorkspaceTestCase implements Comparable<WorkspaceTestCase> {

	private final TextProbeEnvironment env;
	private final String srcFilePath;
	private final Probe probe;

	public WorkspaceTestCase(TextProbeEnvironment env, String srcFilePath, Probe probe) {
		this.env = env;
		this.srcFilePath = srcFilePath;
		this.probe = probe;
	}

	public String getSrcFilePath() {
		return srcFilePath;
	}

	public boolean isVarDecl() {
		return probe != null && probe.type == Type.VARDECL;
	}

	// name() is used by JUnit to label the test case
	public String name() {
		String suffix;
		if (env == null) {
			suffix = "Parser failure";
		} else if (probe == null) {
			suffix = "Semantic Errors";
		} else {
			suffix = (probe.start.line) + " -> " + probe.pp();
		}
		return srcFilePath + ":" + suffix;
	}

	@Override
	public int compareTo(WorkspaceTestCase o) {
		final int cmp = srcFilePath.compareTo(o.srcFilePath);
		if (cmp != 0) {
			return cmp;
		}
		return Integer.compare(probe.start.line, o.probe.start.line);
	}

	public void doAssert(boolean expectPass) {
		if (env == null) {
			if (expectPass) {
				fail("Parsing failures");
			}
			return;
		}
		if (probe == null) {
			if (expectPass) {
				fail("Semantic Errors");
			}
			return;
		}

		switch (probe.type) {
		case QUERY: {
			final Query query = probe.asQuery();
			final QueryResult result = env.evaluateQuery(query);
			if (result == null) {
				if (expectPass) {
					fail(env.errMsgs.isEmpty() ? "No matching nodes" : env.errMsgs.toString());
				}
				return;
			}

			if (!(query.assertion.isPresent())) {
				return;
			}

			final boolean ok = env.evaluateComparison(query, result);
			if (expectPass) {
				assertTrue(env.errMsgs.toString(), ok);
			} else {
				assertFalse(env.errMsgs.toString(), ok);
			}
			break;
		}
		case VARDECL: {
			final boolean didFail = env.loadVariable(probe.asVarDecl().name.value) == null;
			if (didFail == expectPass) {
				assertFalse(env.errMsgs.toString(), true);
			}
			break;
		}
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
		return listTextProbesInWorkspace(tool, dir, (String[]) null);
	}

	public static List<WorkspaceTestCase> listTextProbesInWorkspace(UnderlyingTool tool, File dir, String[] mainArgs)
			throws IOException {
		final WorkspaceHandler wsh = new WorkspaceHandler(dir);
		return listTextProbesInWorkspace(tool, dir, new DefaultRequestHandler(tool, mainArgs, null, wsh));
	}

	public static List<WorkspaceTestCase> listTextProbesInWorkspace(UnderlyingTool tool, File dir,
			DefaultRequestHandler requestHandler) throws IOException {
		final WorkspaceHandler wsh = requestHandler.getWorkspaceHandler();
		final List<WorkspaceTestCase> ret = new ArrayList<>();
		listWorkspaceFilePaths(wsh, null, fullPath -> {
			final ParsingSource psrc = ParsingSource.fromWorkspacePath(fullPath);

			final Document doc = Parser.parse(LazyParser.extractText(psrc, wsh), '[', ']');
			if (doc.containers.isEmpty()) {
				return;
			}
			final ParsedAst parsedAst = requestHandler
					.performParsedRequest(lp -> lp.parse(TextProbeEnvironment.createParsingRequestData(fullPath)));
			if (parsedAst.info == null) {
				// Failed parsing input file. Add dummy case for reporting parse error
				ret.add(new WorkspaceTestCase(null, fullPath, null));
				return;
			}
			final TextProbeEnvironment env = new TextProbeEnvironment(parsedAst.info, doc);
			if (!doc.problems().isEmpty()) {
				// There are static errors. Add dummy case
				// for reporting them
				ret.add(new WorkspaceTestCase(env, fullPath, null));
			} else {
				for (Container c : doc.containers) {
					Probe probe = c.probe();
					if (probe != null) {
						ret.add(new WorkspaceTestCase(env, fullPath, probe));
					}
				}
			}
		});
		return ret;
	}
}
