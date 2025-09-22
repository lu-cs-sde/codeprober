package codeprober.textprobe;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.data.AsyncRpcUpdateValue;
import codeprober.protocol.data.EvaluatePropertyReq;
import codeprober.protocol.data.EvaluatePropertyRes;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.PropertyEvaluationResult;
import codeprober.protocol.data.PropertyEvaluationResult.Type;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.SynchronousEvaluationResult;
import codeprober.protocol.data.TALStep;
import codeprober.protocol.data.WorkerTaskDone;
import codeprober.requesthandler.LazyParser;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;

public class TextProbeEnvironment {

	public static enum VariableLoadStatus {
		NONE, LOAD_SUCCESS, LOAD_ERR,
	};

	private VariableLoadStatus varLoadStatus = VariableLoadStatus.NONE;
	private final JsonRequestHandler requestHandler;
	private final ParsingRequestData parsingRequestData;

	public final ParsedTextProbes parsedFile;
	public final Map<String, List<RpcBodyLine>> variables = new HashMap<>();
	public final List<String> errMsgs = new ArrayList<>();

	private final StdIoInterceptor interceptor;
	private final boolean runConcurrent;

	public boolean printExpectedValuesInComparisonFailures = true;

	public TextProbeEnvironment(JsonRequestHandler requestHandler, WorkspaceHandler wsHandler,
			ParsingSource srcContents, StdIoInterceptor interceptor, boolean runConcurrent) {
		this.requestHandler = requestHandler;
		final String posRecoveryOverride = System.getProperty("cpr.posRecoveryStrategy");
		this.parsingRequestData = new ParsingRequestData(
				posRecoveryOverride != null ? PositionRecoveryStrategy.valueOf(posRecoveryOverride)
						: PositionRecoveryStrategy.ALTERNATE_PARENT_CHILD,
				AstCacheStrategy.PARTIAL, srcContents, null, ".tmp");
		this.parsedFile = ParsedTextProbes.fromFileContents(LazyParser.extractText(srcContents, wsHandler));
		this.interceptor = interceptor;
		this.runConcurrent = runConcurrent;
	}

	public void loadVariables() {
		final int preErr = errMsgs.size();
		for (VarAssignMatch assign : parsedFile.assignments) {
			final TextQueryMatch srcVal = assign.matchSrcAsQuery();
			if (srcVal == null) {
				errMsgs.add("Invalid syntax");
			} else {
				if (srcVal.full.length() != assign.srcVal.length()) {
					errMsgs.add("Invalid syntax");
				} else if (srcVal.nodeType.startsWith("$")) {
					errMsgs.add("Cannot use variables on right-hand side");
				} else {

					final String attrFilter = srcVal.attrNames.length > 0 ? srcVal.attrNames[0] : "";
					final List<NodeLocator> listing = listNodes(assign.lineIdx, attrFilter,
							String.format("this<:%s&@lineSpan~=%d", srcVal.nodeType, assign.lineIdx + 1));
					if (listing == null || listing.isEmpty()) {
						errMsgs.add("No matching nodes");
						continue;
					}
					final List<RpcBodyLine> evalRes = evaluateQuery(srcVal);
					if (evalRes != null) {
						if (variables.containsKey(assign.varName)) {
							errMsgs.add("Duplicate definition of " + assign.varName);
						} else {
							variables.put(assign.varName, evalRes);
						}
					}
				}
			}
		}
		if (errMsgs.size() == preErr) {
			varLoadStatus = VariableLoadStatus.LOAD_SUCCESS;
		} else {
			varLoadStatus = VariableLoadStatus.LOAD_ERR;
		}
	}

	public VariableLoadStatus getVariableStatus() {
		return varLoadStatus;
	}

	public List<NodeLocator> listNodes(int line, String attrPredicate, String tailPredicate) {
		final TALStep rootNode = new TALStep("<ROOT>", null, ((line + 1) << 12) + 1, ((line + 1) << 12) + 4095, 0);
		final SynchronousEvaluationResult result = performEvalReq(parsingRequestData,
				new NodeLocator(rootNode, Collections.emptyList()), //
				new Property("m:NodesWithProperty", Arrays.asList( //
						PropertyArg.fromString(attrPredicate), //
						PropertyArg.fromString(tailPredicate) //
				)));
		final List<RpcBodyLine> body = result.body;
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

	private SynchronousEvaluationResult performEvalReq(ParsingRequestData src, NodeLocator locator, Property property) {

		if (!runConcurrent) {
			final EvaluatePropertyReq req = new EvaluatePropertyReq(src, locator, property, false, null, null, null,
					null, null, true);
			final ClientRequest clientReq = new ClientRequest(req.toJSON(), obj -> {
			}, new AtomicBoolean(true), (p) -> {
			});
			if (interceptor != null) {
				interceptor.install();
			}
			try {
				final PropertyEvaluationResult resp = EvaluatePropertyRes
						.fromJSON(requestHandler.handleRequest(clientReq)).response;
				if (resp.type == Type.job) {
					System.err.println("Unexpected async property response, to synchronous request");
					System.exit(1);
				}
				return resp.asSync();
			} finally {
				if (interceptor != null) {
					interceptor.restore();
				}
			}
		}
		final CountDownLatch cdl = new CountDownLatch(1);
		final SynchronousEvaluationResult[] resPtr = new SynchronousEvaluationResult[1];
		if (interceptor != null) {
			interceptor.install();
		}
		try {
			final EvaluatePropertyReq req = new EvaluatePropertyReq(src, locator, property, false,
					(long) (Math.random() * 100000.0), null, null, null, null, true);

			final ClientRequest clientReq = new ClientRequest(req.toJSON(), obj -> {
				if (obj.value.type == AsyncRpcUpdateValue.Type.workerTaskDone) {
					final WorkerTaskDone tdone = obj.value.asWorkerTaskDone();
					switch (tdone.type) {
					case normal:
						final PropertyEvaluationResult resp = EvaluatePropertyRes.fromJSON(tdone.asNormal()).response;
						if (resp.type == Type.job) {
							System.err.println("Got async result in 'workerTaskDone' callback");
						} else {
							resPtr[0] = resp.asSync();
						}
						break;
					case unexpectedError:
						System.err.println("Failed request: " + tdone.asUnexpectedError());
						break;
					}
					cdl.countDown();
				}
			}, new AtomicBoolean(true), (p) -> {

			});
			final EvaluatePropertyRes initialRes = EvaluatePropertyRes
					.fromJSON(requestHandler.handleRequest(clientReq));
			switch (initialRes.response.type) {
			case sync:
				System.out.println("Weird: got got sync response in concurrent environment");
				return initialRes.response.asSync();
			case job:
				break;
			}
			try {
				cdl.await();
			} catch (InterruptedException e) {
				System.err.println("Interrupted while waiting for concurrent request to finish");
				e.printStackTrace();
			}
		} finally {
			if (interceptor != null) {
				interceptor.restore();
			}
		}
		return resPtr[0];
	}

	public List<RpcBodyLine> evaluateQuery(TextQueryMatch query) {
		List<RpcBodyLine> body;
		if (query.nodeType.startsWith("$")) {
			body = variables.get(query.nodeType);
			if (body == null) {
				errMsgs.add("No such variable");
				return null;
			}
			if (query.attrNames.length == 0) {
				return body;
			}
		} else {
			final TALStep rootNode = new TALStep("<ROOT>", null, ((query.lineIdx + 1) << 12) + 1,
					((query.lineIdx + 1) << 12) + 4095, 0);
			final SynchronousEvaluationResult result = performEvalReq(parsingRequestData,
					new NodeLocator(rootNode, Collections.emptyList()), //
					new Property("m:NodesWithProperty", Arrays.asList( //
							PropertyArg.fromString(query.attrNames.length > 0 ? query.attrNames[0] : ""), //
							PropertyArg.fromString(
									String.format("this<:%s&@lineSpan~=%d", query.nodeType, query.lineIdx + 1)) //
					)));
			body = result.body;
			if (body.size() == 0 || body.get(0).type != RpcBodyLine.Type.arr) {
				errMsgs.add("No matching nodes");
				return null;
			}
			body = body.get(0).asArr();
		}

		final List<NodeLocator> nodes = body.stream() //
				.filter(RpcBodyLine::isNode) //
				.map(RpcBodyLine::asNode) //
				.collect(Collectors.toList());

		NodeLocator locator;
		if (query.nodeIndex != null) {
			if (query.nodeIndex >= 0 && query.nodeIndex < nodes.size()) {
				locator = nodes.get(query.nodeIndex);
			} else {
				errMsgs.add("Invalid index");
				return null;
			}
		} else {
			if (nodes.size() == 1) {
				locator = nodes.get(0);
			} else {
				errMsgs.add(nodes.isEmpty() ? "No matching nodes"
						: String.format("%d nodes of type \"%s\". Add [idx] to disambiguate, e.g. \"%s[0]\"",
								nodes.size(), query.nodeType, query.nodeType));
				return null;
			}
		}
		if (query.attrNames.length == 0) {
			return Arrays.asList(RpcBodyLine.fromNode(locator));
		}
		final SynchronousEvaluationResult resp = performEvalReq(parsingRequestData, locator, new Property( //
				"m:AttrChain", //
				Arrays.asList(query.attrNames).stream().map(x -> PropertyArg.fromString(x)).collect(Collectors.toList()) //
		));
		return resp.body;
	}

	public boolean evaluateComparison(TextAssertionMatch tam, List<RpcBodyLine> lhsBody) {
		if (tam.expectVal == null) {
			// No assertion, automatic pass
			return true;
		}
		List<RpcBodyLine> rhsBody;
		if (tam.expectVal.startsWith("$")) {
			// High resolution comparison
			final TextQueryMatch rhsMatch = TextProbeParser.matchTextQuery(tam.expectVal, tam.lineIdx, tam.columnIdx);
			if (rhsMatch == null) {
				errMsgs.add("Invalid syntax");
				return false;
			}
			if (rhsMatch.full.length() != tam.expectVal.length()) {
				errMsgs.add("Invalid syntax");
				return false;
			}

			rhsBody = evaluateQuery(rhsMatch);
			if (rhsBody == null) {
				return false;
			}
		} else {
			rhsBody = Arrays.asList(RpcBodyLine.fromPlain(tam.expectVal));
		}

		if (lhsBody.size() == 0 || rhsBody.size() == 0) {
			errMsgs.add("Failed evaluation");
			return false;
		}

		final String lhs, rhs;
		final boolean rawComparison;
		if (tam.tilde) {
			// Either a contains-in-array checking, or string.contains checking
			if (lhsBody.get(0).isArr() && rhsBody.get(0).isNode()) {
				final byte[] rhsArr = preparePropertyBodyForHighIshPrecisionComparison(rhsBody.subList(0, 1));

				rawComparison = lhsBody.get(0).asArr().stream().anyMatch(
						x -> Arrays.equals(preparePropertyBodyForHighIshPrecisionComparison(Arrays.asList(x)), rhsArr));
			} else {
				rawComparison = flattenBody(lhsBody).contains(flattenBody(rhsBody));
			}
			final boolean adjustedComparison = tam.exclamation ? !rawComparison : rawComparison;
			if (adjustedComparison) {
				return true;
			}
			if (printExpectedValuesInComparisonFailures) {
				errMsgs.add("-        Expected: " + flattenBody(lhsBody));
				errMsgs.add("   " + (tam.exclamation ? "NOT t" : "   T") + "o contain: " + flattenBody(rhsBody));
			} else {
				errMsgs.add("Actual:" + flattenBody(rhsBody));
			}
			return false;
		}

		// Non-contains checking. Either precise node locator comparison, or less
		// precise toString-comparison
		String notTheSame = "";
		if (lhsBody.get(0).isNode() && rhsBody.get(0).isNode()) {
			rawComparison = Arrays.equals(preparePropertyBodyForHighIshPrecisionComparison(lhsBody.subList(0, 1)),
					preparePropertyBodyForHighIshPrecisionComparison(rhsBody.subList(0, 1)));
			if (!rawComparison && !tam.exclamation) {
				// Default "expected/actual" prints can be very confusing if the ast types are
				// the same
				if (lhsBody.get(0).asNode().result.type.equals(rhsBody.get(0).asNode().result.type)) {
					notTheSame = " (same type, not the same node)";
				}
			}
		} else {
			rawComparison = flattenBody(lhsBody).equals(flattenBody(rhsBody));
		}
		lhs = flattenBody(lhsBody);
		rhs = flattenBody(rhsBody);
		final boolean adjustedComparison = tam.exclamation ? !rawComparison : rawComparison;
		if (adjustedComparison) {
			return true;
		} else {
			if (printExpectedValuesInComparisonFailures) {
				errMsgs.add("-    " + (tam.exclamation ? "Expected NOT: " : "    Expected: ") + rhs);
			}
			errMsgs.add("           Actual: " + lhs + notTheSame);
			return false;
		}
	}

	public static byte[] preparePropertyBodyForHighIshPrecisionComparison(List<RpcBodyLine> lines) {
		final List<RpcBodyLine> filtered = lines.stream() //
				.filter(x -> !(x.type == RpcBodyLine.Type.plain && x.asPlain().trim().length() == 0)) //
				.collect(Collectors.toList());

		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final DataOutputStream dos = new DataOutputStream(baos);
		try {
			for (RpcBodyLine line : filtered) {
				line.writeTo(dos);
			}
		} catch (IOException impossible) {
			System.out.println("Impossible exception happened");
			impossible.printStackTrace();
			System.exit(1);
		}
		return baos.toByteArray();
	}

	public static String flattenBody(List<RpcBodyLine> body) {
		return flattenLine(body.size() == 1 ? body.get(0) : RpcBodyLine.fromArr(body));
	}

	public static String flattenLine(RpcBodyLine line) {
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
						&& arr.get(idx + 1).type == RpcBodyLine.Type.plain && arr.get(idx + 1).asPlain().equals("\n")) {
					final String justNode = flattenLine(arr.get(idx));
					if (arr.size() == 2) {
						return justNode;
					}
					mapped.add(justNode);
					++idx;
				} else {
					mapped.add(flattenLine(arr.get(idx)));
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
			return flattenLine(line.asTracing().result);
		default:
			System.err.println("Unnown rpc line type " + line.type);
			System.exit(1);
			return "";
		}
	}
}
