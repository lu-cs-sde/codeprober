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
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
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
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;
import codeprober.textprobe.ast.ASTNode;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Document;
import codeprober.textprobe.ast.Label;
import codeprober.textprobe.ast.Probe;
import codeprober.textprobe.ast.PropertyAccess;
import codeprober.textprobe.ast.Query;
import codeprober.textprobe.ast.QueryAssert;
import codeprober.textprobe.ast.QueryHead;

public class TextProbeEnvironment {

	public static enum VariableLoadStatus {
		NONE, LOAD_SUCCESS, LOAD_ERR,
	};

	public static class ErrorMessage {
		public final ASTNode context;
		public final String message;

		public ErrorMessage(ASTNode context, String message) {
			this.context = context;
			this.message = message;
		}

		@Override
		public int hashCode() {
			return Objects.hash(context, message);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof ErrorMessage)) {
				return false;
			}
			final ErrorMessage other = (ErrorMessage) obj;
			return Objects.equals(context, other.context) && Objects.equals(message, other.message);
		}

		public String toString() {
			String loc = context.loc();
			StringBuilder res = new StringBuilder(loc);
			final String[] lines = message.split("\n");
			for (int i = 0; i < lines.length; ++i) {
				if (i == 0) {
					res.append(" ");
				} else {
					res.append("\n ");
					for (int j = 0; j < loc.length(); ++j) {
						res.append(" ");
					}
				}
				res.append(lines[i]);
			}
			return res.toString();
		}
	}

	private VariableLoadStatus varLoadStatus = VariableLoadStatus.NONE;
	private final JsonRequestHandler requestHandler;
	public final ParsingRequestData parsingRequestData;

//	public final ParsedTextProbes parsedFile;
	public final Document document;
	public final Map<String, Query> variables = new HashMap<>();
	public final List<ErrorMessage> errMsgs = new ArrayList<>();

	private final StdIoInterceptor interceptor;
	private final boolean runConcurrent;

	public boolean printExpectedValuesInComparisonFailures = true;

	public TextProbeEnvironment(JsonRequestHandler requestHandler, WorkspaceHandler wsHandler,
			ParsingSource srcContents, Document document, StdIoInterceptor interceptor, boolean runConcurrent) {
		this.requestHandler = requestHandler;
		final String posRecoveryOverride = System.getProperty("cpr.posRecoveryStrategy");
		final String cacheStrategyOverride = System.getProperty("cpr.astCacheStrategy");
		this.parsingRequestData = new ParsingRequestData(
				posRecoveryOverride != null ? PositionRecoveryStrategy.valueOf(posRecoveryOverride)
						: PositionRecoveryStrategy.ALTERNATE_PARENT_CHILD,
				cacheStrategyOverride != null ? AstCacheStrategy.valueOf(cacheStrategyOverride)
						: AstCacheStrategy.PARTIAL,
				srcContents, null, ".tmp");
		this.document = document;
		this.interceptor = interceptor;
		this.runConcurrent = runConcurrent;
	}

	public void loadVariables() {
		final int preErr = errMsgs.size();
		for (Container cont : document.containers) {
			final Probe probe = cont.probe();
			if (probe != null && probe.type == Probe.Type.VARDECL) {
				evaluateQuery(probe.asVarDecl().src);
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

	public SynchronousEvaluationResult performEvalReq(ParsingRequestData src, NodeLocator locator, Property property) {
		return performEvalReq(src, locator, property, null);
	}

	public SynchronousEvaluationResult performEvalReq(ParsingRequestData src, NodeLocator locator, Property property,
			List<List<PropertyArg>> attrChainArgs) {

		if (!runConcurrent) {
			final EvaluatePropertyReq req = new EvaluatePropertyReq(src, locator, property, false, null, null, null,
					null, null, true, attrChainArgs);
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

	private void addErr(ASTNode context, String msg) {
		errMsgs.add(new ErrorMessage(context, msg));
	}

	public NodeLocator evaluateQueryHead(QueryHead head, Integer index) {
		if (head.type == QueryHead.Type.VAR) {
			throw new IllegalArgumentException("Unexpected VAR QueryHead");
		}
		final String headType = head.asType().value;
//		query = query.inflate();
		final TALStep rootNode = new TALStep("<ROOT>", null, ((head.start.line) << 12) + 1,
				(head.start.line << 12) + 4095, 0);
		final SynchronousEvaluationResult result = performEvalReq(parsingRequestData,
				new NodeLocator(rootNode, Collections.emptyList()), //
				new Property("m:NodesWithProperty", Arrays.asList( //
						PropertyArg.fromString(""), //
						PropertyArg.fromString(String.format("this<:%s&@lineSpan~=%d", headType, head.start.line)) //
				)));
		final List<RpcBodyLine> ret = result.body;
		if (ret.size() == 0 || ret.get(0).type != RpcBodyLine.Type.arr) {
			addErr(head, "No matching nodes");
			return null;
		}

		final List<NodeLocator> nodes = ret.get(0).asArr().stream() //
				.filter(RpcBodyLine::isNode) //
				.map(RpcBodyLine::asNode) //
				.collect(Collectors.toList());

		NodeLocator locator;
		if (index != null) {
			if (index >= 0 && index < nodes.size()) {
				locator = nodes.get(index);
			} else {
				addErr(head, String.format("Invalid index, outside expected range [%d..%d]", 0, nodes.size()));
				return null;
			}
		} else {
			if (nodes.size() == 1) {
				locator = nodes.get(0);
			} else {
				addErr(head,
						nodes.isEmpty() ? "No matching nodes"
								: String.format("%d nodes %s. Add [idx] to disambiguate, e.g. \"%s[0]\"", //
										nodes.size(), //
										head.type == QueryHead.Type.VAR //
												? String.format("in var \"%s\"", headType)
												: String.format("type \"%s\"", headType),
										head.pp()));
				return null;
			}
		}
		return locator;
	}

	public List<List<PropertyArg>> mapPropAccessesToArgLists(Iterable<PropertyAccess> accesses) {
		boolean anyHasArgs = false;
		for (PropertyAccess acc : accesses) {
			if (acc.arguments.isPresent()) {
				anyHasArgs = true;
				break;
			}
		}
		if (!anyHasArgs) {
			// No args, no need to decode anything
			return null;
		}

		List<List<PropertyArg>> attrChainArgs = new ArrayList<>();
		for (PropertyAccess step : accesses) {
			if (!step.arguments.isPresent()) {
				attrChainArgs.add(Collections.emptyList());
				continue;
			}
			attrChainArgs.add(step.arguments.get().stream().map(arg -> {
				switch (arg.type) {
				case INT:
					return PropertyArg.fromInteger(arg.asInt());

				case QUERY:
					throw new RuntimeException("TODO");

				case STRING:
					return PropertyArg.fromString(arg.asString());

				default:
					throw new IllegalArgumentException("Invalid arg type " + arg.type);
				}
			}).collect(Collectors.toList()));
		}
		return attrChainArgs;
	}

	public List<RpcBodyLine> evaluateQuery(Query query) {
		return evaluateQuery(query, true);
	}

	public List<RpcBodyLine> evaluateQuery(Query query, boolean automaticallyExtractErrors) {

		query = query.inflate();
		final NodeLocator locator = evaluateQueryHead(query.head, query.index);
		if (locator == null) {
			return null;
		}
		if (query.tail.isEmpty()) {
			return Arrays.asList(RpcBodyLine.fromNode(locator));
		}

		final List<List<PropertyArg>> attrChainArgs = mapPropAccessesToArgLists(query.tail);
		final List<RpcBodyLine> resp = performEvalReq(parsingRequestData, locator, new Property( //
				"m:AttrChain", //
				query.tail.stream().map(x -> PropertyArg.fromString(x.name.value)).collect(Collectors.toList()) //
		), attrChainArgs).body;
		if (automaticallyExtractErrors && resp.size() == 1) {
			final RpcBodyLine line = resp.get(0);
			if (line.isPlain()) {
				final String msg = line.asPlain();
				if (msg.startsWith("No such attribute '") || msg.startsWith("Failed evaluating '")
						|| Pattern.compile("Expected \\d+ arguments?, got \\d+").matcher(msg).matches()) {
					addErr(query.tail.isEmpty() ? query.tail.getChild(0) : query, msg);
					return null;
				}
			}
		}
		return resp;
	}

	public boolean evaluateComparison(Query tam, List<RpcBodyLine> lhsBody) {
		if (!tam.assertion.isPresent()) {
			// No assertion, automatic pass
			return true;
		}
		final QueryAssert qassert = tam.assertion.get();
		List<RpcBodyLine> rhsBody;
		switch (qassert.expectedValue.type) {
		case QUERY: {
			rhsBody = evaluateQuery(qassert.expectedValue.asQuery());
			if (rhsBody == null) {
				return false;
			}
			break;
		}
		case CONSTANT:
			rhsBody = Arrays.asList(RpcBodyLine.fromPlain(qassert.expectedValue.asConstant().value));
			break;
		default:
			throw new IllegalArgumentException("Unknown ExpectedValue " + qassert.expectedValue.type);
		}

		final String lhs, rhs;
		final boolean rawComparison;
		if (qassert.tilde) {
			// Either a contains-in-array checking, or string.contains checking
			if (lhsBody.get(0).isArr() && rhsBody.get(0).isNode()) {
				final byte[] rhsArr = preparePropertyBodyForHighIshPrecisionComparison(rhsBody.subList(0, 1));

				rawComparison = lhsBody.get(0).asArr().stream().anyMatch(
						x -> Arrays.equals(preparePropertyBodyForHighIshPrecisionComparison(Arrays.asList(x)), rhsArr));
			} else {
				rawComparison = flattenBody(lhsBody).contains(flattenBody(rhsBody));
			}
			final boolean adjustedComparison = qassert.exclamation ? !rawComparison : rawComparison;
			if (adjustedComparison) {
				return true;
			}
			String prefix = "";
			if (printExpectedValuesInComparisonFailures) {
				prefix = String.format("Expected: %s %s contain: %s\n", //
						flattenBody(lhsBody), qassert.exclamation ? "NOT to" : "   To", //
						flattenBody(rhsBody));
			} else {
				addErr(qassert.expectedValue, prefix + "Actual:" + flattenBody(rhsBody));
			}
			return false;
		}

		// Non-contains checking. Either precise node locator comparison, or less
		// precise toString-comparison
		String notTheSame = "";
		if (lhsBody.get(0).isNode() && rhsBody.get(0).isNode()) {
			rawComparison = Arrays.equals(preparePropertyBodyForHighIshPrecisionComparison(lhsBody.subList(0, 1)),
					preparePropertyBodyForHighIshPrecisionComparison(rhsBody.subList(0, 1)));
			if (!rawComparison && !qassert.exclamation) {
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
		final boolean adjustedComparison = qassert.exclamation ? !rawComparison : rawComparison;
		if (adjustedComparison) {
			return true;
		} else {

			String msg = "";
			if (printExpectedValuesInComparisonFailures) {
				if (qassert.exclamation) {
					msg = "Expected NOT: " + rhs + "\n      ";
				} else {
					msg = "Expected: " + rhs + "\n  ";
				}
			}

			msg += "Actual: " + lhs + notTheSame;

			// Try to extract extra information for the message if the
			// cpr_getAssertFailSuffix API is implemented
			{
				List<PropertyAccess> concatList = new ArrayList<>(tam.tail.toList());
				concatList.add(new PropertyAccess(tam.start, tam.end,
						new Label(tam.start, tam.end, "cpr_getAssertFailSuffix")));
				final Query extendedQuery = new Query(tam.start, tam.end, tam.head, tam.index, concatList);
				tam.enclosingContainer().adopt(extendedQuery);
				List<RpcBodyLine> betterMessage = evaluateQuery(extendedQuery, false);
				if (betterMessage != null) {
					if (betterMessage.size() == 1 && betterMessage.get(0).isPlain()
							&& betterMessage.get(0).asPlain().startsWith("No such attribute '")) {
						// The API is not implemented
					} else {
						// It is implemented
						msg += ", " + flattenBody(betterMessage);
					}
				}
			}
			addErr(qassert.expectedValue, msg);
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
