package codeprober.requesthandler;

import java.util.Arrays;
import java.util.List;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.Span;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.data.HoverReq;
import codeprober.protocol.data.HoverRes;
import codeprober.requesthandler.LazyParser.ParsedAst;

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

	public static HoverRes apply(HoverReq req, LazyParser parser) {
		final ParsedAst parsed = parser.parse(req.src);
		return new HoverRes(extract0(parsed, "cpr_ide_hover", req.line, req.column));
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
