package codeprober.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import org.json.JSONObject;
import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.locator.CreateLocator;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.data.AsyncRpcUpdate;
import codeprober.protocol.data.AsyncRpcUpdateValue;
import codeprober.protocol.data.EvaluatePropertyReq;
import codeprober.protocol.data.EvaluatePropertyRes;
import codeprober.protocol.data.GetWorkspaceFileReq;
import codeprober.protocol.data.GetWorkspaceFileRes;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.PropertyEvaluationResult;
import codeprober.protocol.data.RequestAdapter;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.SynchronousEvaluationResult;
import codeprober.protocol.data.WorkerTaskDone;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;
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

		};
	}

	private MergedResult run(BiFunction<String, List<PropertyArg>, RpcBodyLine> evalQuery, WorkspaceFile... files) {
		final WorkspaceHandler wsHandler = createWorkspaceHandler(files);
		return RunWorkspaceTest.run(new JsonRequestHandler() {

			@Override
			public JSONObject handleRequest(ClientRequest request) {
				return new RequestAdapter() {
					protected ListWorkspaceDirectoryRes handleListWorkspaceDirectory(ListWorkspaceDirectoryReq req) {
						return wsHandler.handleListWorkspaceDirectory(req);
					}

					@Override
					protected EvaluatePropertyRes handleEvaluateProperty(EvaluatePropertyReq req) {
						return new EvaluatePropertyRes(
								PropertyEvaluationResult.fromSync(new SynchronousEvaluationResult(
										Arrays.asList(evalQuery.apply(req.property.name, req.property.args)), 0, 0, 0,
										0, 0, 0, 0)));
					}

				}.handle(request.data);
			}
		}, 0, wsHandler);
	}

	@Test
	public void testEmptyWorkspace() {
		assertEquals(MergedResult.ALL_PASS, run(null));
	}

	@Test
	public void testSingleFileWithoutContent() {
		assertEquals(MergedResult.ALL_PASS, run(null, new WorkspaceFile("foo.txt", "bar")));
	}

	private BiFunction<String, List<PropertyArg>, RpcBodyLine> getSimpleQueryFunc() {
		final AstNode ast = new AstNode(TestData.getSimple());
		final AstInfo info = TestData.getInfo(ast);
		final RpcBodyLine pointer = RpcBodyLine.fromNode(CreateLocator.fromNode(info, ast));
		return new BiFunction<String, List<PropertyArg>, RpcBodyLine>() {
			@Override
			public RpcBodyLine apply(String query, List<PropertyArg> args) {
				switch (query) {
				case "m:NodesWithProperty":
					return RpcBodyLine.fromArr(Arrays.asList(pointer));
				case "ptr":
					return pointer;
				case "x":
					return RpcBodyLine.fromPlain("a");
				case "y":
					return RpcBodyLine.fromPlain("b");
				case "m:AttrChain":
					return apply(args.get(args.size() - 1).asString(), Collections.emptyList());
				default:
					return RpcBodyLine.fromPlain("??? query=" + query);
				}
			}
		};
	}

	@Test
	public void testSingleFileProbePass() {
		assertEquals(MergedResult.ALL_PASS, run(getSimpleQueryFunc(), new WorkspaceFile("foo.txt", "bar [[Baz.x=a]]")));
	}

	@Test
	public void testSingleFileProbeFail() {
		assertEquals(MergedResult.SOME_FAIL,
				run(getSimpleQueryFunc(), new WorkspaceFile("foo.txt", "bar [[Baz.x=b]]")));
	}

	@Test
	public void testSingleFileWithPointerSteps() {
		assertEquals(MergedResult.ALL_PASS,
				run(getSimpleQueryFunc(), new WorkspaceFile("foo.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y=b]]")));
	}

	@Test
	public void testMultipleFilesWithPointerSteps() {
		assertEquals(MergedResult.ALL_PASS, run(getSimpleQueryFunc(), //
				new WorkspaceFile("foo.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y=b]]"), //
				new WorkspaceFile("bar.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y=b]]"), //
				new WorkspaceFile("baz.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y=b]]") //
		));
	}

	private void runStressTest(MergedResult expectedResult, String lastFileContents) {

		final WorkspaceHandler wsHandler = createWorkspaceHandler( //
				new WorkspaceFile("foo0.txt", "bar [[Baz.ptr.ptr.ptr.ptr.x=a]]"), //
				new WorkspaceFile("foo1.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y=b]]"), //
				new WorkspaceFile("foo2.txt", "bar [[Baz.ptr.ptr.ptr.ptr.x!=b]]"), //
				new WorkspaceFile("foo3.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y!=a]]"), //
				new WorkspaceFile("foo4.txt", "bar [[Baz.ptr.ptr.ptr.ptr.x=a]]"), //
				new WorkspaceFile("foo5.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y=b]]"), //
				new WorkspaceFile("foo6.txt", "bar [[Baz.ptr.ptr.ptr.ptr.x!=b]]"), //
				new WorkspaceFile("foo7.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y!=a]]"), //
				new WorkspaceFile("foo8.txt", "bar [[Baz.ptr.ptr.ptr.ptr.x=a]]"), //
				new WorkspaceFile("foo9.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y=b]]"), //
				new WorkspaceFile("fooA.txt", "bar [[Baz.ptr.ptr.ptr.ptr.x!=b]]"), //
				new WorkspaceFile("fooB.txt", "bar [[Baz.ptr.ptr.ptr.ptr.y!=a]]"), //
				new WorkspaceFile("fooC.txt", "bar " + lastFileContents));

		final List<Runnable> pendingResponses = new CopyOnWriteArrayList<>();
		final BiFunction<String, List<PropertyArg>, RpcBodyLine> evalQuery = getSimpleQueryFunc();

		final AtomicBoolean running = new AtomicBoolean(true);
		new Thread(() -> {
			// Very short initial sleep, let the jobs queue up
			try {
				Thread.sleep(1L);
			} catch (InterruptedException e) {
				fail(e.toString());
			}
			try {
				while (running.get()) {
					if (!pendingResponses.isEmpty()) {
						pendingResponses.remove((int) (Math.random() * pendingResponses.size())).run();
					}
				}
			} catch (RuntimeException e) {
				System.out.println("ERR: " + e);
				System.exit(1);
			}
		}).start();
		final MergedResult result = RunWorkspaceTest.run(new JsonRequestHandler() {

			@Override
			public JSONObject handleRequest(ClientRequest request) {
				return new RequestAdapter() {
					protected ListWorkspaceDirectoryRes handleListWorkspaceDirectory(ListWorkspaceDirectoryReq req) {
						return wsHandler.handleListWorkspaceDirectory(req);
					}

					@Override
					protected EvaluatePropertyRes handleEvaluateProperty(EvaluatePropertyReq req) {
						if (req.job == null) {
							fail("All requests are expected to have job IDs");
						}

						final Runnable respond = () -> {
							SynchronousEvaluationResult res = new SynchronousEvaluationResult(
									Arrays.asList(evalQuery.apply(req.property.name, req.property.args)), 0, 0, 0, 0, 0,
									0, 0);
							request.sendAsyncResponse(new AsyncRpcUpdate(req.job, true,
									AsyncRpcUpdateValue.fromWorkerTaskDone(WorkerTaskDone
											.fromNormal(new EvaluatePropertyRes(PropertyEvaluationResult.fromSync(res))
													.toJSON()))));
						};
						pendingResponses.add(respond);
						return new EvaluatePropertyRes(PropertyEvaluationResult.fromJob(req.job));
					}

				}.handle(request.data);
			}
		}, 8 /* high worker count, higher chance to provoke deadlocks */, wsHandler);

		running.set(false);

		assertEquals(expectedResult, result);
	}

	@Test
	public void testMultiFileConcurrencyStressTest() {
		for (int i = 0; i < 32; ++i) {
			runStressTest(MergedResult.ALL_PASS, "[[Baz=TestData$Program]]");
		}
	}

	@Test
	public void testMultiFileConcurrencyStressTestWithFailure() {
		for (int i = 0; i < 32; ++i) {
			runStressTest(MergedResult.SOME_FAIL, "[[Baz=IntentionalFailure]]");
		}
	}
}
