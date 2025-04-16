package codeprober.util;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.json.JSONObject;

import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.data.EvaluatePropertyReq;
import codeprober.protocol.data.EvaluatePropertyRes;
import codeprober.protocol.data.GetTestSuiteReq;
import codeprober.protocol.data.GetTestSuiteRes;
import codeprober.protocol.data.GetWorkspaceFileReq;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.PropertyEvaluationResult.Type;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.TALStep;
import codeprober.protocol.data.TestSuite;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;
import codeprober.textprobe.TextAssertionMatch;
import codeprober.textprobe.TextProbeEnvironment;

public class RunWorkspaceTest {

	public static enum MergedResult {
		ALL_PASS, SOME_FAIL,
	}

	private JsonRequestHandler requestHandler;
	int numPass = 0;
	int numFail = 0;
	StdIoInterceptor interceptor;
	final WorkspaceHandler workspaceHandler = new WorkspaceHandler();

	private static boolean verbose = "true".equals(System.getProperty("cpr.verbose"));

	private RunWorkspaceTest(JsonRequestHandler requestHandler) {
		this.requestHandler = requestHandler;

		final PrintStream out = System.out;
		final PrintStream err = System.err;
		interceptor = new StdIoInterceptor(false) {
			@Override
			public void onLine(boolean stdout, String line) {
				if (verbose) {
					(stdout ? out : err).println(line);
				}
			}
		};
	}

	public static MergedResult run(JsonRequestHandler requestHandler) {
		// Use the API (WorkspaceHandler) to mimic Codeprober client behavior as close
		// as possible
		final RunWorkspaceTest rwt = new RunWorkspaceTest(requestHandler);
		rwt.runDirectory(null);
		System.out.println("Done: " + rwt.numPass + " pass, " + rwt.numFail + " fail");
		return (rwt.numFail > 0) ? MergedResult.SOME_FAIL : MergedResult.ALL_PASS;
	}

	private void runDirectory(String workspacePath) {
		final ListWorkspaceDirectoryRes res = workspaceHandler
				.handleListWorkspaceDirectory(new ListWorkspaceDirectoryReq(workspacePath));
		if (res.entries == null) {
			System.err.println("Invalid path in workspace: " + workspacePath);
			return;
		}

		final String parentPath = workspacePath == null ? "" : (workspacePath + "/");
		for (WorkspaceEntry e : res.entries) {
			switch (e.type) {
			case directory: {
				runDirectory(parentPath + e.asDirectory());
				break;
			}
			case file: {
				final int startNumPass = numPass;
				final int startNumFail = numFail;
				final String fullPath = parentPath + e.asFile();
				final String fileContents = workspaceHandler
						.handleGetWorkspaceFile(new GetWorkspaceFileReq(fullPath)).content;
//				System.out.println("Looking at file " + fullPath);
				if (fileContents == null) {
					System.err.println("Invalid file in workspace: " + fullPath);
					break;
				}
				final TextProbeEnvironment env = new TextProbeEnvironment(requestHandler, fileContents, interceptor);
				env.loadVariables();
				numFail += env.errMsgs.size();

				for (TextAssertionMatch tam : env.parsedFile.assertions) {
					final int preErrSize = env.errMsgs.size();
					final List<RpcBodyLine> actual = env.evaluateQuery(tam);
					if (actual != null) {
						if (env.evaluateComparison(tam, actual)) {
							++numPass;
						}
					}
					if (env.errMsgs.size() != preErrSize) {
						++numFail;
					}
				}
				if (numPass == startNumPass && numFail == startNumFail) {
					// No tests, don't output anything
					System.out.println("Nothing in file " + fullPath + "..");
				} else if (numFail == startNumFail) {
					System.out.println("  ✅ " + fullPath);
				} else {
					System.out.println("  ❌ " + fullPath);
					for (String errMsg : env.errMsgs) {
						System.out.println("     " + errMsg);
					}

				}
			}
			}
		}
	}

	private static ClientRequest constructMessage(JSONObject query) {
		return new ClientRequest(query, obj -> {
		}, new AtomicBoolean(true));
	}

	public static List<NodeLocator> listNodes(JsonRequestHandler requestHandler, ParsingRequestData prd, int line,
			String attrPredicate, String tailPredicate) {
		final TALStep rootNode = new TALStep("<ROOT>", null, ((line + 1) << 12) + 1, ((line + 1) << 12) + 4095, 0);
		final EvaluatePropertyRes result = EvaluatePropertyRes.fromJSON(requestHandler.handleRequest( //
				constructMessage(new EvaluatePropertyReq( //
						prd, new NodeLocator(rootNode, Collections.emptyList()), //
						new Property("m:NodesWithProperty", Arrays.asList( //
								PropertyArg.fromString(attrPredicate), //
								PropertyArg.fromString(tailPredicate) //
						)), //
						false).toJSON())));
		if (result.response.type != Type.sync) {
			System.err.println("Unexpected async property response, are we running concurrently?");
			System.exit(1);
		}
		final List<RpcBodyLine> body = result.response.asSync().body;
		if (body.size() == 0 || body.get(0).type != RpcBodyLine.Type.arr) {
			System.out.println("Unexpected respose from search query: "
					+ body.stream().map(x -> x.toJSON().toString()).collect(Collectors.joining("; ")));
			return null;
		}
		return body.get(0).asArr().stream() //
				.filter(RpcBodyLine::isNode) //
				.map(RpcBodyLine::asNode) //
				.collect(Collectors.toList());
	}

	public TestSuite getTestSuiteContents(String suiteName) {
		return GetTestSuiteRes.fromJSON(requestHandler.handleRequest( //
				constructMessage(new GetTestSuiteReq(suiteName).toJSON()))).result.asContents();
	}

}
