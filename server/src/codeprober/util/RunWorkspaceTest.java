package codeprober.util;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONObject;

import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.data.EvaluatePropertyReq;
import codeprober.protocol.data.EvaluatePropertyRes;
import codeprober.protocol.data.GetTestSuiteReq;
import codeprober.protocol.data.GetTestSuiteRes;
import codeprober.protocol.data.GetWorkspaceFileReq;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.PropertyEvaluationResult.Type;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.TALStep;
import codeprober.protocol.data.TestSuite;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;

public class RunWorkspaceTest {

	public static enum MergedResult {
		ALL_PASS, SOME_FAIL,
	}

	private JsonRequestHandler requestHandler;
	int numPass = 0;
	int numFail = 0;
	List<String> errMsgs = new ArrayList<>();
	StdIoInterceptor interceptor;

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
		final ListWorkspaceDirectoryRes res = WorkspaceHandler
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
				errMsgs.clear();
				final String fullPath = parentPath + e.asFile();
				final String contents = WorkspaceHandler
						.handleGetWorkspaceFile(new GetWorkspaceFileReq(fullPath)).content;
				if (contents == null) {
					System.err.println("Invalid file in workspace: " + fullPath);
					break;
				}

				final Pattern textProbePattern = Pattern
						.compile("\\[\\[(\\w+)(\\[\\d+\\])?((?:\\.\\w+)+)(!?)(~?)(?:=(((?!\\[\\[).)*))\\]\\](?!\\])");

				final ParsingRequestData prd = new ParsingRequestData(PositionRecoveryStrategy.ALTERNATE_PARENT_CHILD,
						AstCacheStrategy.PARTIAL, ParsingSource.fromText(contents + "\n"), null, "");

				final String[] lines = contents.split("\n");
				for (int lineIdx = 0; lineIdx < lines.length; ++lineIdx) {
					Matcher matcher = textProbePattern.matcher(lines[lineIdx]);
					while (matcher.find()) {
//					      const [full, nodeType, attrName, exclamation, tilde, expectVal] = match;
//						final String full = matcher.group(0);
						final String nodeType = matcher.group(1);
						final String nodeIndex = matcher.group(2);
						final String rawAttrNames = matcher.group(3);
						final boolean exclamation = "!".equals(matcher.group(4));
						final boolean tilde = "~".equals(matcher.group(5));
						final String expectVal = matcher.group(6);

						final String[] attrNames = rawAttrNames.substring(1).split("\\.");
//						System.out.println("Matched @ " + matcher.start() + ": " + full + " ;; " + nodeType + ";; "
//								+ attrName + ";; " + exclamation + ";; " + tilde + ";; " + expectVal);

						final List<NodeLocator> matches = new ArrayList<>();
						final int fLineIdx = lineIdx;
						interceptor.install();
						try {
							final List<NodeLocator> listing = listNodes(prd, fLineIdx, attrNames[0],
									String.format("this<:%s&@lineSpan~=%d", nodeType, fLineIdx + 1));
							if (listing == null || listing.isEmpty()) {
								++numFail;
								errMsgs.add("No matching nodes");
								System.out
										.println("!! no match for " + attrNames[0] + ", " + fLineIdx + ", " + nodeType);
								continue;
							}
							matches.addAll(listing);
						} finally {
							interceptor.restore();
						}

						NodeLocator subject;
						if (nodeIndex != null) {
							final int parsed = Integer.parseInt(nodeIndex.substring(1, nodeIndex.length() - 1));
							if (parsed >= 0 && parsed < matches.size()) {
								subject = matches.get(parsed);
							} else {
								++numFail;
								errMsgs.add("Invalid index");
								continue;
							}
						} else {
							if (matches.size() == 1) {
								subject = matches.get(0);
							} else {
								++numFail;
								errMsgs.add(matches.size() + " nodes of type \"" + nodeType
										+ "\". Add [idx] to disambiguate, e.g. \"" + nodeType + "[0]\"");
								continue;
							}
						}

						if (matches.isEmpty()) {
							// Failure
							++numFail;
						} else {
//							++numPass; // TODO
							final String actual;
							interceptor.install();
							try {
								actual = evaluateProperty(prd, subject, attrNames);
							} finally {
								interceptor.restore();
							}
							if (actual != null) {
								final boolean rawComparison = tilde ? actual.contains(expectVal)
										: actual.equals(expectVal);
								final boolean adjustedComparison = exclamation ? !rawComparison : rawComparison;

								if (adjustedComparison) {
									++numPass;
								} else {
									++numFail;
									errMsgs.add("- Expected: " + (exclamation ? "NOT " : "") + expectVal);
									errMsgs.add("    Actual: " + (exclamation ? "    " : "") + actual);
								}
							} else {
								// An error was already logged down in evaluateProperty, no need to do anthing
							}

						}
					}
				}
				if (numPass == startNumPass && numFail == startNumFail) {
					// No tests, don't output anything
				} else if (numFail == startNumFail) {
					System.out.println("  ✅ " + fullPath);
				} else {
					System.out.println("  ❌ " + fullPath);
					for (String errMsg : errMsgs) {
						System.out.println("     " + errMsg);
					}

				}
			}
			}
		}
	}

	private ClientRequest constructMessage(JSONObject query) {
		return new ClientRequest(query, obj -> {
		}, new AtomicBoolean(true));
	}

	public List<NodeLocator> listNodes(ParsingRequestData prd, int line, String attrPredicate, String tailPredicate) {
		System.out.println("Listing on line " + line + " w/ attrPred: " + attrPredicate + ", tail:" + tailPredicate);
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

	private NodeLocator evaluateReferenceProperty(ParsingRequestData prd, NodeLocator locator, String attrName) {
		final EvaluatePropertyRes result = EvaluatePropertyRes.fromJSON(requestHandler.handleRequest( //
				constructMessage(new EvaluatePropertyReq( //
						prd, locator, //
						new Property(attrName), //
						false).toJSON())));
		if (result.response.type != Type.sync) {
			System.err.println("Unexpected async property response, are we running concurrently?");
			System.exit(1);
		}
		final List<RpcBodyLine> body = result.response.asSync().body;
		if (body.size() == 0 || body.get(0).type != RpcBodyLine.Type.node) {
			return null;
		}
		return body.get(0).asNode();
	}

	public String evaluateProperty(ParsingRequestData prd, NodeLocator locator, String[] attrChain) {
		if (attrChain.length > 1) {
			for (int intermediateIdx = 0; intermediateIdx < attrChain.length - 1; intermediateIdx++) {
				locator = evaluateReferenceProperty(prd, locator, attrChain[intermediateIdx]);
				if (locator == null) {
					++numFail;
					errMsgs.add("Invalid attribute chain");
					return null;
				}
			}
		}
		final EvaluatePropertyRes result = EvaluatePropertyRes.fromJSON(requestHandler.handleRequest( //
				constructMessage(new EvaluatePropertyReq( //
						prd, locator, //
						new Property(attrChain[attrChain.length - 1]), //
						false).toJSON())));
		if (result.response.type != Type.sync) {
			System.err.println("Unexpected async property response, are we running concurrently?");
			System.exit(1);
		}

		final AtomicReference<Function<RpcBodyLine, String>> converter = new AtomicReference<>(null);
		converter.set(line -> {
			switch (line.type) {
			case plain:
				return line.asPlain();
			case stdout:
				return line.asStdout();
			case stderr:
				return line.asStderr();
			case streamArg:
				return line.asStreamArg();
			case dotGraph:
				return line.asDotGraph();
			case html:
				return line.asHtml();
			case arr:
				List<String> mapped = new ArrayList<>();
				List<RpcBodyLine> arr = line.asArr();
				for (int idx = 0; idx < arr.size(); ++idx) {
					if (arr.get(idx).type == RpcBodyLine.Type.node && idx < arr.size() - 1
							&& arr.get(idx + 1).type == RpcBodyLine.Type.plain
							&& arr.get(idx + 1).asPlain().equals("\n")) {
						final String justNode = converter.get().apply(arr.get(idx));
						if (arr.size() == 2) {
							return justNode;
						}
						mapped.add(justNode);
						++idx;
					} else {
						mapped.add(converter.get().apply(arr.get(idx)));
					}
				}
				return "[" + mapped.stream().collect(Collectors.joining(", ")) + "]";
			case node:
				String ret = line.asNode().result.label;
				if (ret == null) {
					ret = line.asNode().result.type;
				}
				return ret.substring(ret.lastIndexOf('.') + 1);
			case highlightMsg:
				return line.asHighlightMsg().msg;
			case tracing:
				return converter.get().apply(line.asTracing().result);
			default:
				System.err.println("Unnown rpc line type " + line.type);
				System.exit(1);
				return "";
			}
		});
		;
		final List<RpcBodyLine> body = result.response.asSync().body;
		return converter.get().apply(body.size() == 1 ? body.get(0) : RpcBodyLine.fromArr(body));
	}

	public TestSuite getTestSuiteContents(String suiteName) {
		return GetTestSuiteRes.fromJSON(requestHandler.handleRequest( //
				constructMessage(new GetTestSuiteReq(suiteName).toJSON()))).result.asContents();
	}

}
