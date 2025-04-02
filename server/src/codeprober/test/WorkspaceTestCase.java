package codeprober.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import codeprober.DefaultRequestHandler;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.data.GetWorkspaceFileReq;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;
import codeprober.toolglue.UnderlyingTool;
import codeprober.util.RunWorkspaceTest;

public class WorkspaceTestCase implements Comparable<WorkspaceTestCase> {

	private final JsonRequestHandler client;
	private final String srcFilePath;
	private final String srcFileContents;
	private final int lineIndex;
	private final String fullTextProbe;
	private final String nodeType;
	private final String nodeIndex;
	private final String[] attrNames;
	private final boolean exclamation;
	private final boolean tilde;
	private final String expectVal;

	public WorkspaceTestCase(JsonRequestHandler client, String srcFilePath, String srcFileContents, int lineIndex,
			String fullTextProbe, String nodeType, String nodeIndex, String[] attrNames, boolean exclamation,
			boolean tilde, String expectVal) {
		this.client = client;
		this.srcFilePath = srcFilePath;
		this.srcFileContents = srcFileContents;
		this.lineIndex = lineIndex;
		this.fullTextProbe = fullTextProbe;
		this.nodeType = nodeType;
		this.nodeIndex = nodeIndex;
		this.attrNames = attrNames;
		this.exclamation = exclamation;
		this.tilde = tilde;
		this.expectVal = expectVal;
	}

	public String getSrcFilePath() {
		return srcFilePath;
	}

	// name() is used by JUnit to label the test case
	public String name() {
		return srcFilePath + ":" + (lineIndex + 1) + " -> " + fullTextProbe;
	}

	@Override
	public int compareTo(WorkspaceTestCase o) {
		final int cmp = srcFilePath.compareTo(o.srcFilePath);
		if (cmp != 0) {
			return cmp;
		}
		return Integer.compare(lineIndex, o.lineIndex);
	}

	public void doAssert(boolean expectPass) {
		final ParsingRequestData prd = new ParsingRequestData(PositionRecoveryStrategy.FAIL, AstCacheStrategy.FULL,
				ParsingSource.fromText(srcFileContents), null, "");

		final List<NodeLocator> matches = new ArrayList<>();
		final List<NodeLocator> listing = RunWorkspaceTest.listNodes(client, prd, lineIndex, attrNames[0],
				String.format("this<:%s&@lineSpan~=%d", nodeType, lineIndex + 1));
		if (listing == null || listing.isEmpty()) {
			if (expectPass) {
				fail("No matching nodes");
			}
			return;
		}
		matches.addAll(listing);

		NodeLocator subject;
		if (nodeIndex != null) {
			final int parsed = Integer.parseInt(nodeIndex.substring(1, nodeIndex.length() - 1));
			if (parsed >= 0 && parsed < matches.size()) {
				subject = matches.get(parsed);
			} else {
				if (expectPass) {
					fail("Invalid node index");
				}
				return;
			}
		} else {
			if (matches.size() == 1) {
				subject = matches.get(0);
			} else {
				if (expectPass) {
					fail(matches.size() + " nodes of type \"" + nodeType + "\". Add [idx] to disambiguate, e.g. \""
							+ nodeType + "[0]\"");
				}
				return;
			}
		}

		final String actual = RunWorkspaceTest.evaluateProperty(client, prd, subject, attrNames);

		if (exclamation) {
			if (tilde) {
				if (expectPass) {
					assertTrue("expected <[" + actual + "]> to not contain <[" + expectVal + "]>",
							!actual.contains(expectVal));
				} else {
					assertFalse("expected <[" + actual + "]> to contain <[" + expectVal + "]>",
							actual.contains(expectVal));
				}
			} else {
				if (expectPass) {
					assertNotEquals(expectVal, actual);
				} else {
					assertEquals(expectVal, actual);
				}
			}
		} else {
			if (tilde) {
				if (expectPass) {
					assertTrue("expected <[" + actual + "]> to contain <[" + expectVal + "]>",
							actual.contains(expectVal));
				} else {
					assertFalse("expected <[" + actual + "]> to not contain <[" + expectVal + "]>",
							actual.contains(expectVal));
				}
			} else {
				if (expectPass) {
					assertEquals(expectVal, actual);
				} else {
					assertNotEquals(expectVal, actual);
				}
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
				final String contents = workspaceHandler
						.handleGetWorkspaceFile(new GetWorkspaceFileReq(fullPath)).content;
				if (contents != null) {
					Pattern textProbePattern = RunWorkspaceTest.getTextProbePattern();

					final String[] lines = contents.split("\n");
					for (int lineIdx = 0; lineIdx < lines.length; ++lineIdx) {
						Matcher matcher = textProbePattern.matcher(lines[lineIdx]);
						while (matcher.find()) {
							final String fullMatch = matcher.group(0);
							final String nodeType = matcher.group(RunWorkspaceTest.PATTERN_GROUP_NODETYPE);
							final String nodeIndex = matcher.group(RunWorkspaceTest.PATTERN_GROUP_NODEINDEX);
							final String rawAttrNames = matcher.group(RunWorkspaceTest.PATTERN_GROUP_ATTRNAMES);
							final boolean exclamation = "!"
									.equals(matcher.group(RunWorkspaceTest.PATTERN_GROUP_EXCLAMATION));
							final boolean tilde = "~".equals(matcher.group(RunWorkspaceTest.PATTERN_GROUP_TILDE));
							final String expectVal = matcher.group(RunWorkspaceTest.PATTERN_GROUP_EXPECTVAL);

							out.add(new WorkspaceTestCase(requestHandler, fullPath, contents, lineIdx, fullMatch,
									nodeType, nodeIndex, rawAttrNames.substring(1).split("\\."), exclamation, tilde,
									expectVal));
						}
					}
				}
			}
		}
	}

	public static List<WorkspaceTestCase> listTextProbesInWorkspace(UnderlyingTool tool, File dir) throws IOException {
		final JsonRequestHandler requestHandler = new DefaultRequestHandler(tool);
		final WorkspaceHandler wsh = new WorkspaceHandler(dir);
		final List<WorkspaceTestCase> ret = new ArrayList<>();
		listAllWorkspaceFiles(requestHandler, wsh, null, ret);
		return ret;
	}
}
