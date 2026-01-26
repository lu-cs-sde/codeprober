package codeprober.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import codeprober.DefaultRequestHandler;
import codeprober.ast.TestData;
import codeprober.protocol.data.GetWorkspaceFileReq;
import codeprober.protocol.data.GetWorkspaceFileRes;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.toolglue.ParseResult;
import codeprober.util.RunWorkspaceTest.MergedResult;

public class TestRunWorkspaceTest {

	private static class WorkspaceFile {
		public final String name;
		public final String contents;

		public WorkspaceFile(String name, String contents) {
			this.name = name;
			this.contents = contents;
		}
	}

	private WorkspaceHandler createWorkspaceHandler(WorkspaceFile... files) {
		return new WorkspaceHandler() {
			public ListWorkspaceDirectoryRes handleListWorkspaceDirectory(ListWorkspaceDirectoryReq req) {
				final List<WorkspaceEntry> ret = new ArrayList<>();

				final String reqPath = req.path != null ? req.path : "";
				if (reqPath.equals("")) {
					for (WorkspaceFile file : files) {
						ret.add(WorkspaceEntry.fromFile(new codeprober.protocol.data.WorkspaceFile(file.name)));
					}
				}
				return new ListWorkspaceDirectoryRes(ret);
			}

			@Override
			public GetWorkspaceFileRes handleGetWorkspaceFile(GetWorkspaceFileReq req) {
				for (WorkspaceFile file : files) {
					if (file.name.equals(req.path)) {
						return new GetWorkspaceFileRes(file.contents);
					}
				}
				return new GetWorkspaceFileRes();
			};

			@Override
			public File getWorkspaceFile(String path) {
				return new File(path);
			}

		};
	}

	private MergedResult run(WorkspaceFile... files) {
		final WorkspaceHandler wsHandler = createWorkspaceHandler(files);
		return RunWorkspaceTest
				.run(new DefaultRequestHandler(p -> new ParseResult(TestData.getMultipleAmbiguousLevels()), wsHandler));
	}

	@Test
	public void testEmptyWorkspace() {
		assertEquals(MergedResult.ALL_PASS, run());
	}

	@Test
	public void testSingleFileWithoutContent() {
		assertEquals(MergedResult.ALL_PASS, run(new WorkspaceFile("foo.txt", "bar")));
	}

	@Test
	public void testSingleFileProbePass() {
		assertEquals(MergedResult.ALL_PASS, run(new WorkspaceFile("foo.txt", "bar [[Baz.x=a]]")));
	}

	@Test
	public void testSingleFileProbeFail() {
		assertEquals(MergedResult.SOME_FAIL,
				run(new WorkspaceFile("foo.txt", "bar [[Baz.x=b]]")));
	}

	@Test
	public void testSingleFileWithPointerSteps() {
		assertEquals(MergedResult.ALL_PASS,
				run(new WorkspaceFile("foo.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y=b]]")));
	}

	@Test
	public void testMultipleFilesWithPointerSteps() {
		assertEquals(MergedResult.ALL_PASS, run(//
				new WorkspaceFile("foo.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y=b]]"), //
				new WorkspaceFile("bar.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y=b]]"), //
				new WorkspaceFile("baz.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y=b]]") //
		));
	}
}
