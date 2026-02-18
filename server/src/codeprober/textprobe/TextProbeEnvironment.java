package codeprober.textprobe;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.CreateLocator;
import codeprober.locator.CreateLocator.LocatorMergeMethod;
import codeprober.locator.NodesWithProperty;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.create.EncodeResponseValue;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.requesthandler.DecorationsHandler;
import codeprober.textprobe.ast.ASTList;
import codeprober.textprobe.ast.ASTNode;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Document;
import codeprober.textprobe.ast.Expr;
import codeprober.textprobe.ast.Probe;
import codeprober.textprobe.ast.PropertyAccess;
import codeprober.textprobe.ast.Query;
import codeprober.textprobe.ast.QueryAssert;
import codeprober.textprobe.ast.QueryHead;
import codeprober.textprobe.ast.TypeQueryHead;
import codeprober.textprobe.ast.VarDecl;

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
	public final AstInfo info;

	public final Document document;
	public final Map<String, QueryResult> cachedVariableValues = new HashMap<>();
	public final List<ErrorMessage> errMsgs = new ArrayList<>();

	public boolean printExpectedValuesInComparisonFailures = true;

	public TextProbeEnvironment(AstInfo info, Document document) {
		this.info = info;
		this.document = document;
	}

	public static ParsingRequestData createParsingRequestData(String workspacePath) {
		final String posRecoveryOverride = System.getProperty("cpr.posRecoveryStrategy");
		final String cacheStrategyOverride = System.getProperty("cpr.astCacheStrategy");
		return new ParsingRequestData(
				posRecoveryOverride != null ? PositionRecoveryStrategy.valueOf(posRecoveryOverride)
						: PositionRecoveryStrategy.ALTERNATE_PARENT_CHILD,
				cacheStrategyOverride != null ? AstCacheStrategy.valueOf(cacheStrategyOverride)
						: AstCacheStrategy.PARTIAL,
				ParsingSource.fromWorkspacePath(workspacePath), null, ".tmp");
	}

	public void loadVariables() {
		final int preErr = errMsgs.size();
		for (Container cont : document.containers) {
			final Probe probe = cont.probe();
			if (probe != null && probe.type == Probe.Type.VARDECL) {
				final VarDecl vdec = probe.asVarDecl();
				final String vname = vdec.name.value;
				if (!cachedVariableValues.containsKey(vname)) {
					cachedVariableValues.put(vname, evaluateQuery(vdec.src));
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
		final List<Object> rawListing = NodesWithProperty.get(info, info.ast, attrPredicate, tailPredicate, 128);
		final LocatorMergeMethod savedMergeMethod = CreateLocator.getMergeMethod();
		CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
		try {
			return rawListing //
					.stream() //
					.filter(x -> x instanceof AstNode) //
					.map(x -> CreateLocator.fromNode(info, (AstNode) x)) //
					.filter(x -> x != null) //
					.collect(Collectors.toList()); //
		} finally {
			CreateLocator.setMergeMethod(savedMergeMethod);
		}
	}

	private void addErr(ASTNode context, String msg) {
		errMsgs.add(new ErrorMessage(context, msg));
	}

	public QueryResult evaluateQueryHead(QueryHead head, Integer index) {

		switch (head.type) {
		case VAR:
			final String vname = head.asVar().value;
			if (!cachedVariableValues.containsKey(vname)) {
				final Query src = head.asVar().decl().src;
				cachedVariableValues.put(vname, evaluateQuery(src));
			}
			return cachedVariableValues.get(vname);

		case TYPE:
			final TypeQueryHead tqHead = head.asType();
			final List<AstNode> nodes = NodesWithProperty
					.get(info, info.ast, "",
							String.format("this<:%s&@lineSpan~=%d", tqHead.label.value, tqHead.bumpedLine()), 128)
					.stream() //
					.filter(x -> x instanceof AstNode) //
					.map(x -> (AstNode) x) //
					.collect(Collectors.toList());

			if (index != null) {
				if (index >= 0 && index < nodes.size()) {
					final Object node = nodes.get(index).underlyingAstNode;
					return new QueryResult(node, node.getClass());
				} else {
					addErr(head, String.format("Invalid index, outside expected range [%d..%d]", 0, nodes.size() - 1));
					return null;
				}
			} else {
				if (nodes.size() == 1) {
					final Object node = nodes.get(0).underlyingAstNode;
					return new QueryResult(node, node.getClass());
				} else {
					addErr(head,
							nodes.isEmpty() ? "No matching nodes"
									: String.format("%d nodes %s. Add [idx] to disambiguate, e.g. \"%s%s[0]\"", //
											nodes.size(), //
											head.type == QueryHead.Type.VAR //
													? String.format("in var \"%s\"", tqHead.label.value)
													: String.format("type \"%s\"", tqHead.label.value),
											tqHead.bumpUp ? "^" : "", //
											head.pp()));
					return null;
				}
			}

		default:
			System.err.println("Unknown QueryHead type " + head.type);
			return null;
		}
	}

	public QueryResult evaluateQuery(Query query) {
		return evaluateQuery(query, true);
	}

	public QueryResult evaluateQuery(Query query, boolean automaticallyExtractErrors) {
		final QueryResult headQres = evaluateQueryHead(query.head, query.index);
		if (headQres == null) {
			return null;
		}
		if (query.tail.isEmpty()) {
			return headQres;
		}

		Object headVal = headQres.value;
		Class<?> headType = headQres.clazz;
		for (int i = 0; i < query.tail.getNumChild(); ++i) {
			final PropertyAccess acc = query.tail.get(i);
			if (headVal == Reflect.VOID_RETURN_VALUE || headVal == null) {
				addErr(acc.name, String.format("No such attribute '%s' on %s", acc.name.value,
						headVal == Reflect.VOID_RETURN_VALUE ? "void" : "null"));
				return null;
			}
			try {
				if (!acc.arguments.isPresent()) {
					final Method mth = Reflect.findMostAccessibleMethod(headVal, acc.name.value);
					headVal = Reflect.invoke0(headVal, acc.name.value);
					headType = mth.getReturnType();
				} else {
					final ASTList<Expr> args = acc.arguments.get();

					final List<Class<?>> argTypes = new ArrayList<>();
					final List<Object> argValues = new ArrayList<>();

					for (int j = 0; j < args.getNumChild(); ++j) {
						final Expr arg = args.get(j);
						switch (arg.type) {
						case INT:
							argTypes.add(Integer.TYPE);
							argValues.add(arg.asInt());
							break;

						case STRING:
							argTypes.add(String.class);
							argValues.add(arg.asString());
							break;

						case QUERY:
							final QueryResult qval = evaluateQuery(arg.asQuery());
							if (qval == null) {
								return null;
							}
							argTypes.add(qval.clazz);
							argValues.add(qval.value);
							break;

						default:
							addErr(arg, "Internal Error: Unknown argument type " + arg.type);
							return null;
						}
					}

					final Method mth = Reflect.findCompatibleMethod(headVal.getClass(), acc.name.value,
							argTypes.toArray(new Class[argTypes.size()]));

					headVal = Reflect.invokeN(headVal, mth, argValues.toArray(new Object[argValues.size()]));
					headType = mth.getReturnType();
				}
			} catch (InvokeProblem ip) {
				Throwable cause = ip.getCause();
				if (cause instanceof InvocationTargetException) {
					cause = cause.getCause();
				}
				if (cause instanceof NoSuchMethodException) {
					// Could be that the method exists, but with a different type or number of
					// parameters
					final int actualArgCount = acc.arguments.isPresent() ? acc.arguments.get().getNumChild() : 0;
					String needle = acc.name.value;
					// First, check for same count (indicates wrong actual types)
					for (Method method : headVal.getClass().getMethods()) {
						if (!method.getName().equals(needle)) {
							continue;
						}
						final int formalParamCount = method.getParameterCount();
						final boolean isVarargs = method.isVarArgs();
						if (formalParamCount == actualArgCount
								|| (isVarargs && actualArgCount >= formalParamCount - 1)) {
							// Bingo! This means that the method exists, but expects different parameter
							// types.
							final Class<?>[] paramTypes = method.getParameterTypes();
							StringBuilder exp = new StringBuilder();
							for (int j = 0; j < paramTypes.length; ++j) {
								if (j > 0) {
									exp.append(", ");
								}
								if (isVarargs && j == paramTypes.length - 1) {
									exp.append(String.format("%s...", paramTypes[j].getComponentType().getName()));
								} else {
									exp.append(paramTypes[j].getName());
								}
							}
							addErr(acc.name, String.format(paramTypes.length > 1 || isVarargs //
									? "Expected parameter types: [%s]" //
									: "Expected parameter type: %s", exp));
							return null;
						}
					}
					// Second, check for different count (indicates wrong number of args)
					for (Method method : headVal.getClass().getMethods()) {
						if (!method.getName().equals(needle)) {
							continue;
						}
						final int formalParamCount = method.getParameterCount();
						addErr(acc.name,
								String.format("Expected %s argument(s), got %s", formalParamCount, actualArgCount));
						return null;
					}
					// Third, no such attribute simply exists
					addErr(acc.name, String.format("No such attribute '%s' on %s", acc.name.value,
							headVal.getClass().getName()));
					return null;
				}
				if (cause instanceof AssertionError) {
					final String detail = cause.getMessage();
					addErr(acc, detail.isEmpty() ? "Assertion Failed" : detail);
					return null;
				}
				System.out.println(
						String.format("Error while evaluting %s.%s", headVal.getClass().getName(), acc.name.value));
				cause.printStackTrace(System.out);
				addErr(acc.name, String.format("Failed evaluating '%s' on %s", acc.name.value, headVal.getClass()));
				return null;
			}
		}

		if (headVal == Reflect.VOID_RETURN_VALUE) {
			return new QueryResult(null, Void.TYPE);
		}
		return new QueryResult(headVal, headType);
	}

	public boolean evaluateComparison(Query tam, QueryResult lhsBody) {
		if (!tam.assertion.isPresent()) {
			// No assertion, automatic pass
			return true;
		}
		final QueryAssert qassert = tam.assertion.get();
		QueryResult rhsBody;
		switch (qassert.expectedValue.type) {
		case QUERY: {
			rhsBody = evaluateQuery(qassert.expectedValue.asQuery());
			if (rhsBody == null) {
				return false;
			}
			break;
		}
		case INT:
			rhsBody = new QueryResult(qassert.expectedValue.asInt(), Integer.TYPE);
			break;
		case STRING:
			rhsBody = new QueryResult(qassert.expectedValue.asString(), String.class);
			break;
		default:
			throw new IllegalArgumentException("Unknown ExpectedValue " + qassert.expectedValue.type);
		}

		if (qassert.tilde) {
			// String.contains checking
			final String lhsStr = flattenBody(lhsBody);
			final String rhsStr = flattenBody(rhsBody);
			final boolean rawComparison = lhsStr.contains(rhsStr);
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
				addErr(qassert.expectedValue, prefix + "Actual: "
						+ DecorationsHandler.wrapLiteralValueInQuotesIfNecessary(flattenBody(lhsBody)));
			}
			return false;
		}

		// Non-contains checking
		final boolean rawComparison;
		if (lhsBody.isStringCoercionCausingLiteral() || rhsBody.isStringCoercionCausingLiteral()) {
			final String lhsStr = flattenBody(lhsBody);
			final String rhsStr = flattenBody(rhsBody);
			rawComparison = lhsStr.equals(rhsStr);
		} else if (lhsBody.value == null || rhsBody.value == null) {
			rawComparison = lhsBody.value == rhsBody.value;
		} else if (lhsBody.value == rhsBody.value) {
			// Optimize for identity comparisons
			rawComparison = true;
		} else {
			// Finally, regular .equals() checking
			rawComparison = lhsBody.value.equals(rhsBody.value);
		}

		final boolean adjustedComparison = qassert.exclamation ? !rawComparison : rawComparison;
		if (adjustedComparison) {
			return true;
		} else {
			final String lhs = DecorationsHandler.wrapLiteralValueInQuotesIfNecessary(flattenBody(lhsBody));
			final String rhs = DecorationsHandler.wrapLiteralValueInQuotesIfNecessary(flattenBody(rhsBody));

			String msg = "";
			if (printExpectedValuesInComparisonFailures) {
				if (qassert.exclamation) {
					msg = "Expected NOT: " + rhs + "\n      ";
				} else {
					msg = "Expected: " + rhs + "\n  ";
				}
			}
			String notTheSame = "";
			if (!qassert.exclamation && lhs.equals(rhs)) {
				// Same string representation, different values
				notTheSame += " (same string representation, but .equals() returned false)";
			}

			msg += "Actual: " + lhs + notTheSame;

			// Try to extract extra information for the message if the
			// cpr_getAssertFailSuffix API is implemented
			{

				try {
					String suffix = String.valueOf(Reflect.invoke0(lhsBody.value, "cpr_getAssertFailSuffix"));
					msg += ", " + suffix;
				} catch (InvokeProblem e) {
					// OK, this is optional anyway
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

	public String flattenBody(QueryResult qres) {
		if (qres.value == null) {
			return "null";
		}
		if (!Parser.permitImplicitStringConversion && qres.isStringCoercionCausingLiteral()) {
			return String.valueOf(qres.value);
		}
		final boolean expandSave = EncodeResponseValue.shouldExpandListNodes;
		EncodeResponseValue.shouldExpandListNodes = false;
		try {
			final List<RpcBodyLine> body = new ArrayList<>();
			EncodeResponseValue.encodeTyped(info, body, null, qres.value, new HashSet<>());
			return flattenLine(body.size() == 1 ? body.get(0) : RpcBodyLine.fromArr(body));
		} finally {
			EncodeResponseValue.shouldExpandListNodes = expandSave;
		}
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

	public static class QueryResult {
		public final Object value;
		public final Class<?> clazz;

		public QueryResult(Object value, Class<?> clazz) {
			this.value = value;
			this.clazz = clazz;
		}

		public boolean isStringCoercionCausingLiteral() {
			return value instanceof String || value instanceof Integer || value instanceof Boolean;
		}
	}
}
