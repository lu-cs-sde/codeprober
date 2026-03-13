package codeprober.requesthandler;

import java.util.ArrayList;
import java.util.List;

import codeprober.protocol.data.Decoration;
import codeprober.protocol.data.GetDecorationsReq;
import codeprober.protocol.data.GetDecorationsRes;
import codeprober.textprobe.FilteredTextProbeParser;
import codeprober.textprobe.FilteredTextProbeParser.FilteredParse;
import codeprober.textprobe.Parser;
import codeprober.textprobe.TextProbeEnvironment;
import codeprober.textprobe.TextProbeEnvironment.QueryResult;
import codeprober.textprobe.ast.ASTNode;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Probe;
import codeprober.textprobe.ast.Query;

public class DecorationsHandler {

	interface DecorationAdder {
		void add(ASTNode node, String type, String message);
	}

	public static GetDecorationsRes apply(GetDecorationsReq req, WorkspaceHandler workspaceHandler, LazyParser parser) {
		final String txt = LazyParser.extractText(req.src.src, workspaceHandler);
		if (txt == null) {
			return new GetDecorationsRes();
		}
		final FilteredParse fparse = FilteredTextProbeParser.parse(txt, parser, req.src);
		if (fparse.document == null) {
			// No containers in this file
			return new GetDecorationsRes();
		}
		if (req.forceAllOK != null && req.forceAllOK) {
			final List<Decoration> ret = new ArrayList<>();
			for (Container c : fparse.document.containers) {
				final Probe p = c.probe();
				if (p != null) {
					ret.add(new Decoration( //
							c.start.getPackedBits(), c.end.getPackedBits(), //
							"ok", "", p.start.getPackedBits(), //
							p.end.getPackedBits()));

				}
			}
			return new GetDecorationsRes(ret);
		}
		if (fparse.ast == null) {
			List<Decoration> ret = new ArrayList<>();
			for (Container c : fparse.document.containers) {
				if (c.probe() != null) {
					ret.add(new Decoration(c.start.getPackedBits(), c.end.getPackedBits(), "error",
							"Failed parsing input"));
				}
			}
			return new GetDecorationsRes(ret, true);
		}
		final TextProbeEnvironment env = new TextProbeEnvironment(fparse.ast.info, fparse.document);
		if (req.includeExpectedValues != null && req.includeExpectedValues) {
			env.printExpectedValuesInComparisonFailures = true;
		} else {
			env.printExpectedValuesInComparisonFailures = false;
		}
		return apply(env);
	}

	public static GetDecorationsRes apply(TextProbeEnvironment env) {
		final List<Decoration> ret = new ArrayList<>();
		final DecorationAdder addDecoration = (node, type, msg) -> {
			final Container con = node.enclosingContainer();
			final ASTNode context = con != null ? con : node;
			ret.add(new Decoration( //
					context.start.getPackedBits(), context.end.getPackedBits(), //
					type, msg, node.start.getPackedBits(), //
					node.end.getPackedBits()));
		};

		// Phase 1: static semantic issues
		env.document.collectProblems((node, msg) -> addDecoration.add(node, "error", msg));
		if (!ret.isEmpty()) {
			return new GetDecorationsRes(ret);
		}

		// Phase 2: Normal queries
		for (Container container : env.document.containers) {
			final Probe probe = container.probe();
			if (probe == null) {
				continue;
			}

			switch (probe.type) {
			case QUERY: {
				final Query query = probe.asQuery();
				final QueryResult lhs = env.evaluateQuery(query);
				if (query.assertion.isPresent()) {
					if (lhs != null) {
						if (env.evaluateComparison(query, lhs)) {
							addDecoration.add(query, "ok", null);
						}
					}
				} else {
					if (lhs != null) {
						final String flat = env.flattenBody(lhs);
						addDecoration.add(probe, "query", "= " + wrapLiteralValueInQuotesIfNecessary(flat));
					}
				}

				break;
			}
			case VARDECL: {
				if (env.loadVariable(probe.asVarDecl().name.value) != null) {
					addDecoration.add(probe, "var", null);
				}
				break;
			}
			default: {
				System.err.println("Unknown text probe type " + probe.pp());
				break;
			}

			}
		}
		env.errMsgs.forEach(errMsg -> addDecoration.add(errMsg.context, "error", errMsg.message));

		return new GetDecorationsRes(ret);
	}

	public static String wrapLiteralValueInQuotesIfNecessary(String str) {
		if (Parser.permitImplicitStringConversion) {
			// No wrapping needed
			return str;
		}

		switch (str) {
		case "": // Fall-through
		case "null":
		case "true": // Fall-through
		case "false": {
			// OK as-is
			return str;
		}
		default: {
			try {
				Integer.parseInt(str);
				return str;
			} catch (NumberFormatException ignore) {

			}
			// Escape escape-codes
			str = str //
					.replace("\\", "\\\\") //
					.replace("\n", "\\n") //
					.replace("\"", "\\\"");

			// Needs to be wrapped in quotes
			return String.format("\"%s\"", str);
		}
		}
	}
}
