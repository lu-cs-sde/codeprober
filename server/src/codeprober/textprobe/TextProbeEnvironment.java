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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.json.JSONObject;

import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.data.EvaluatePropertyReq;
import codeprober.protocol.data.EvaluatePropertyRes;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.PropertyEvaluationResult.Type;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.TALStep;
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

	public TextProbeEnvironment(JsonRequestHandler requestHandler, String srcFileContents,
			StdIoInterceptor interceptor) {
		this.requestHandler = requestHandler;
		this.parsingRequestData = new ParsingRequestData(PositionRecoveryStrategy.ALTERNATE_PARENT_CHILD,
				AstCacheStrategy.PARTIAL, ParsingSource.fromText(srcFileContents + "\n"), null, "");
		this.parsedFile = ParsedTextProbes.fromFileContents(srcFileContents);
		this.interceptor = interceptor;
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

	private static ClientRequest constructMessage(JSONObject query) {
		return new ClientRequest(query, obj -> {
		}, new AtomicBoolean(true));
	}

	public List<NodeLocator> listNodes(int line, String attrPredicate, String tailPredicate) {
		final TALStep rootNode = new TALStep("<ROOT>", null, ((line + 1) << 12) + 1, ((line + 1) << 12) + 4095, 0);
		final EvaluatePropertyRes result = EvaluatePropertyRes.fromJSON(performRequest( //
				constructMessage(new EvaluatePropertyReq( //
						parsingRequestData, new NodeLocator(rootNode, Collections.emptyList()), //
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

	private JSONObject performRequest(ClientRequest req) {
		if (interceptor != null) {
			interceptor.install();
		}
		try {
			return requestHandler.handleRequest(req);
		} finally {
			if (interceptor != null) {
				interceptor.restore();
			}
		}
	}

	private List<RpcBodyLine> evaluateProp(NodeLocator locator, String prop) {
		final EvaluatePropertyRes result = EvaluatePropertyRes.fromJSON(performRequest( //
				constructMessage(new EvaluatePropertyReq( //
						parsingRequestData, locator, //
						new Property(prop), //
						false).toJSON())));
		if (result.response.type != Type.sync) {
			System.err.println("Unexpected async property response, are we running concurrently?");
			System.exit(1);
		}
		return result.response.asSync().body;
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
			final EvaluatePropertyRes result = EvaluatePropertyRes.fromJSON(performRequest( //
					constructMessage(new EvaluatePropertyReq( //
							parsingRequestData, new NodeLocator(rootNode, Collections.emptyList()), //
							new Property("m:NodesWithProperty", Arrays.asList( //
									PropertyArg.fromString(query.attrNames.length > 0 ? query.attrNames[0] : ""), //
									PropertyArg.fromString(
											String.format("this<:%s&@lineSpan~=%d", query.nodeType, query.lineIdx + 1)) //
							)), //
							false).toJSON())));
			if (result.response.type != Type.sync) {
				System.err.println("Unexpected async property response, are we running concurrently?");
				System.exit(1);
			}
			body = result.response.asSync().body;
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
		List<RpcBodyLine> lastResult = null;
		for (int attrIdx = 0; attrIdx < query.attrNames.length; ++attrIdx) {

			if (attrIdx > 0) {
				if (lastResult.size() >= 1 && lastResult.get(0).type == RpcBodyLine.Type.node) {
					locator = lastResult.get(0).asNode();
				} else {
					errMsgs.add("Invalid attribute chain");
					return null;
				}
			}

			lastResult = evaluateProp(locator, query.attrNames[attrIdx]);
		}

		return lastResult;

	}

	public boolean evaluateComparison(TextAssertionMatch tam, List<RpcBodyLine> lhsBody) {
		if (tam.expectVal.startsWith("$")) {
			// High resolution comparison
			final TextQueryMatch rhsMatch = TextProbeParser.matchTextQuery(tam.expectVal, tam.lineIdx);
			if (rhsMatch == null) {
				errMsgs.add("Invalid syntax");
				return false;
			}
			if (rhsMatch.full.length() != tam.expectVal.length()) {
				errMsgs.add("Invalid syntax");
				return false;
			}

			final List<RpcBodyLine> rhsBody = evaluateQuery(rhsMatch);
			if (rhsBody == null) {
				return false;
			}
			final byte[] lhs = preparePropertyBodyForHighIshPrecisionComparison(lhsBody);
			final byte[] rhs = preparePropertyBodyForHighIshPrecisionComparison(rhsBody);
			if (!Arrays.equals(lhs, rhs)) {
				errMsgs.add("Failed assertion");
				return false;
			}
			return true;
		}

		final String lhs = flattenBody(lhsBody);
		final String rhs = tam.expectVal;
		final boolean rawComparison = tam.tilde ? lhs.contains(rhs) : lhs.equals(rhs);
		final boolean adjustedComparison = tam.exclamation ? !rawComparison : rawComparison;
		if (adjustedComparison) {
			return true;
		} else {
			errMsgs.add("- Expected: " + (tam.exclamation ? "NOT " : "") + rhs);
			errMsgs.add("    Actual: " + (tam.exclamation ? "    " : "") + lhs);
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
