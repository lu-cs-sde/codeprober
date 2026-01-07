package codeprober.requesthandler;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
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
import codeprober.textprobe.DogEnvironment;
import codeprober.textprobe.Parser;
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

		final String txt = LazyParser.extractText(req.src.src, wsHandler);
		if (txt != null) {
			final DogEnvironment env = new DogEnvironment(reqHandler, wsHandler, req.src.src,
					Parser.parse(txt, '[', ']'), null, false);

			// Use the "completionContext" api to help identify what is being hovered
			final CompletionContext compCtx = env.document.completionContextAt(req.line, req.column);
			if (compCtx == null) {
				// No hover to be done here
				return new HoverRes();
			}
			switch (compCtx.type) {
			case QUERY_HEAD_TYPE: {
				final Query q = compCtx.asQueryHead();

				if (q.head.type == Type.VAR && !env.document.problems().isEmpty()) {
					// Cannot reliably interact with variables when there are static errors
					return new HoverRes();
				}
				final NodeLocator subject;
				switch (q.head.type) {
				case VAR: {
					// The var may be defined as [[$var:=SomeOtherType.foo.bar.baz]]
					// In this case, $var may or may not be a node locator at all.
					// Let's find out
					final List<RpcBodyLine> varDef = env.evaluateQuery(q.head.asVar().decl().src);
					if (varDef == null) {
						subject = null;
					} else if (varDef.size() >= 1 && varDef.get(0).type == RpcBodyLine.Type.node) {
						subject = varDef.get(0).asNode();
					} else {
						// Not a node. We _could_ evaluate a shorter chain of steps. The query head is
						// guaranteed to be a node, and some of the intermediate steps may also be
						// nodes. In this case, the last step was not a node.
						// In theory we could iteratively test evaluating with one fewer steps until we
						// get a node. However, if the user is hovering something that isn't a node and
						// we highlight a node, that could easily cause confusion. The solution: just
						// don't highlight anything.
						subject = null;
					}
					break;
				}
				case TYPE: {
					subject = env.evaluateQueryHead(q.inflate().head, q.index);
					break;
				}
				default: {
					System.err.println("Unexpected QueryHead type " + q.head.type);
					return new HoverRes();
				}
				}
				return hoverNodeLocator(q, q.head.start, q.head.end, subject);
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
				Query subQ = new Query(q.start, q.end, q.head, q.index, q.tail.toList().subList(0, hoverAccessIdx));
				q.adopt(subQ);
				Position localStart = q.start;
				Position localEnd = q.tail.isEmpty() ? subQ.end : subQ.tail.get(subQ.tail.getNumChild() - 1).end;
				List<RpcBodyLine> subRes = null;
				if (env.document.problems().isEmpty()) {
					subRes = env.evaluateQuery(subQ.inflate());
					if (subRes == null) {
						return new HoverRes();
					}
					if (subRes.size() >= 1 && subRes.get(0).isNode()) {
						final NodeLocator node = subRes.get(0).asNode();
						return hoverNodeLocator(subQ, localStart, localEnd, node);
					}
				}
				return new HoverRes(subRes == null //
						? Collections.emptyList() //
						: Arrays.asList(DogEnvironment.flattenBody(subRes)), //
						localStart.getPackedBits(), localEnd.getPackedBits() + 1);

			case QUERY_RESULT:
				// Nothing to do here, the query should already be visible in the editor
				return new HoverRes();

			default:
				System.err.println("Unknown completion context: " + compCtx.type);
				return new HoverRes();
			}
		}

		return new HoverRes();
	}

	private static HoverRes hoverNodeLocator(final Query q, Position localStart, Position localEnd,
			final NodeLocator subject) {
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
				: Arrays.asList(DogEnvironment.flattenLine(RpcBodyLine.fromNode(subject))), localStart.getPackedBits(),
				localEnd.getPackedBits() + 1, remoteStart, remoteEnd);
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
