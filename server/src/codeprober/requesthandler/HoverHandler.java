package codeprober.requesthandler;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.CreateLocator;
import codeprober.locator.Span;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.data.HoverReq;
import codeprober.protocol.data.HoverRes;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.requesthandler.LazyParser.ParsedAst;
import codeprober.rpc.JsonRequestHandler;
import codeprober.textprobe.CompletionContext;
import codeprober.textprobe.CompletionContext.PropertyNameDetail;
import codeprober.textprobe.Parser;
import codeprober.textprobe.TextProbeEnvironment;
import codeprober.textprobe.TextProbeEnvironment.QueryResult;
import codeprober.textprobe.ast.Position;
import codeprober.textprobe.ast.PropertyAccess;
import codeprober.textprobe.ast.Query;
import codeprober.textprobe.ast.QueryHead.Type;

public class HoverHandler {

	static class NarrowNodeSearchResult {
		public final int spanDiff;
		public final int depth;
		public final AstNode node;

		public NarrowNodeSearchResult(int spanDiff, int depth, AstNode node) {
			this.spanDiff = spanDiff;
			this.depth = depth;
			this.node = node;
		}
	}

	public static HoverRes apply(HoverReq req, JsonRequestHandler reqHandler, LazyParser parser,
			WorkspaceHandler wsHandler) {
		final ParsedAst parsed = parser.parse(req.src);
		final List<String> customHover = extract0(parsed, "cpr_ide_hover", req.line, req.column);
		if (customHover != null) {
			return new HoverRes(customHover);
		}
		if (parsed.info == null) {
			if (parsed.captures == null) {
				return new HoverRes();
			}
			return new HoverRes(Arrays.asList(TextProbeEnvironment.flattenBody(parsed.captures)));
		}

		final String txt = LazyParser.extractText(req.src.src, wsHandler);
		if (txt != null) {
			final TextProbeEnvironment env = new TextProbeEnvironment(parsed.info, Parser.parse(txt, '[', ']'));

			// Use the "completionContext" api to help identify what is being hovered
			final CompletionContext compCtx = env.document.completionContextAt(req.line, req.column);
			if (compCtx == null) {
				// No hover to be done here
				return new HoverRes();
			}
			switch (compCtx.type) {
			case QUERY_HEAD_TYPE: {
				final Query q = compCtx.asQueryHead();
				if (q == null) {
					// Query not created yet, nothing to hover
					return new HoverRes();
				}

				if (q.head.type == Type.VAR && !env.document.problems().isEmpty()) {
					// Cannot reliably interact with variables when there are static errors
					return new HoverRes();
				}
				final AstNode subjNode;
				switch (q.head.type) {
				case VAR: {
					// The var may be defined as [[$var:=SomeOtherType.foo.bar.baz]]
					// In this case, $var may or may not be a node locator at all.
					// Let's find out
					final QueryResult varDef = env.evaluateQuery(q.head.asVar().decl().src);
					if (varDef == null) {
						subjNode = null;
					} else if (parsed.info.baseAstClazz.isInstance(varDef.value)) {
						subjNode = new AstNode(varDef.value);
					} else {
						// Not a node, just return a string representation of the value
						return new HoverRes(Arrays.asList(env.flattenBody(varDef)), q.head.start.getPackedBits(),
								q.head.end.getPackedBits() + 1);
					}
					break;
				}
				case TYPE: {
					final QueryResult thead = env.evaluateQueryHead(q.head, q.index);
					if (thead == null) {
						subjNode = null;
					} else if (parsed.info.baseAstClazz.isInstance(thead.value)) {
						subjNode = new AstNode(thead.value);
					} else {
						subjNode = null;
					}
					break;
				}
				default: {
					System.err.println("Unexpected QueryHead type " + q.head.type);
					return new HoverRes();
				}
				}
				final NodeLocator subjLoc = subjNode == null ? null : CreateLocator.fromNode(parsed.info, subjNode);
				return hoverNodeLocator(q, q.head.start, q.head.end, subjLoc);
			}

			case PROPERTY_NAME:
				final PropertyNameDetail prop = compCtx.asPropertyName();

				int hoverAccessIdx;
				if (prop.access == null) {
					hoverAccessIdx = prop.query.tail.getNumChild();
				} else {
					hoverAccessIdx = 0;
					for (PropertyAccess infl : prop.query.tail) {
						++hoverAccessIdx;
						if (infl == prop.access) {
							break;
						}
					}
				}
				final Query q = prop.query;
				final Query subQ = new Query(q.start, q.end, q.head, q.index,
						q.tail.toList().subList(0, hoverAccessIdx));
				q.adopt(subQ);
				return hoverQuery(env, subQ);

			case QUERY_RESULT:
				// Nothing to do here, the query should already be visible in the editor
				return new HoverRes();

			case VAR_DECL_NAME: {
				return hoverQuery(env, compCtx.asVarDecl().src);
			}
			default:
				System.err.println("Unknown completion context: " + compCtx.type);
				return new HoverRes();
			}
		}

		return new HoverRes();
	}

	private static HoverRes hoverQuery(TextProbeEnvironment env, final Query q) {
		final Position localStart = q.start;
		final Position localEnd = q.tail.isEmpty() ? q.end : q.tail.get(q.tail.getNumChild() - 1).end;
		QueryResult subRes = null;
		if (env.document.problems().isEmpty()) {
			subRes = env.evaluateQuery(q);
			if (subRes == null) {
				return new HoverRes();
			}

			if (env.info.baseAstClazz.isInstance(subRes.value)) {
				final NodeLocator node = CreateLocator.fromNode(env.info, new AstNode(subRes.value));
				return hoverNodeLocator(q, localStart, localEnd, node);
			}
		}
		return new HoverRes(subRes == null //
				? Collections.emptyList() //
				: Arrays.asList(env.flattenBody(subRes)), //
				localStart.getPackedBits(), localEnd.getPackedBits() + 1);
	}

	private static HoverRes hoverNodeLocator(Query q, Position localStart, Position localEnd, NodeLocator subject) {
		Integer remoteStart, remoteEnd;
		if (subject == null || subject.result.start == 0 || subject.result.end == 0) {
			remoteStart = null;
			remoteEnd = null;
		} else {
			remoteStart = subject.result.start;
			remoteEnd = subject.result.end + 1;
		}
		return new HoverRes(subject == null //
				? Collections.emptyList() //
				: Arrays.asList(TextProbeEnvironment.flattenLine(RpcBodyLine.fromNode(subject))),
				localStart.getPackedBits(), localEnd.getPackedBits() + 1, remoteStart, remoteEnd);
	}

	@SuppressWarnings("unchecked")
	static List<String> extract0(ParsedAst parsed, String prop, int line, int column) {
		if (parsed.info == null) {
			return null;
		}
		final NarrowNodeSearchResult node = findNarrowestNodes(parsed.info, parsed.info.ast, prop,
				(line << 12) + column, 0);
		if (node == null) {
			return null;
		}
		try {
			Object res = Reflect.invoke0(node.node.underlyingAstNode, prop);
			if (res == null) {
				return null;
			}
			if (res instanceof String) {
				return Arrays.asList((String) res);
			}
			if (res instanceof List<?>) {
				return (List<String>) res;
			}
			final String msg = "Invalid return type from " + prop + ", expected String or java.util.List<String>, got "
					+ res.getClass().getName();
			System.err.println(msg);
			return Arrays.asList(msg);
		} catch (InvokeProblem e) {
			System.out.println("Problem when invoking `" + prop + "`:");
			return null;
		}
	}

	static NarrowNodeSearchResult findNarrowestNodes(AstInfo info, AstNode node, String propName, int pos, int depth) {
		final Span nodePos = node.getRecoveredSpan(info);

		if (nodePos.start != 0 && nodePos.end != 0) {
			if (pos < nodePos.start || pos > nodePos.end) {
				return null;
			}
		}
		NarrowNodeSearchResult best = null;
		if (node.hasProperty(info, propName)) {
			best = new NarrowNodeSearchResult(nodePos.end - nodePos.start, depth, node);
		}

		for (AstNode child : node.getChildren(info)) {
			final NarrowNodeSearchResult narrower = findNarrowestNodes(info, child, propName, pos, depth + 1);
			if (narrower == null) {
				continue;
			}
			if (best == null || narrower.spanDiff < best.spanDiff
					|| (narrower.spanDiff == best.spanDiff && narrower.depth > best.depth)) {
				best = narrower;
			}

		}
		return best;
	}

}
