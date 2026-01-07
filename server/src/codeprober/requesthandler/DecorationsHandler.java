package codeprober.requesthandler;

import java.util.ArrayList;
import java.util.List;

import codeprober.protocol.data.Decoration;
import codeprober.protocol.data.GetDecorationsReq;
import codeprober.protocol.data.GetDecorationsRes;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.rpc.JsonRequestHandler;
import codeprober.textprobe.DogEnvironment;
import codeprober.textprobe.Parser;
import codeprober.textprobe.ast.ASTNode;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Probe;
import codeprober.textprobe.ast.Probe.Type;
import codeprober.textprobe.ast.Query;

public class DecorationsHandler {

	interface DecorationAdder {
		void add(ASTNode node, String type, String message);
	}

	public static GetDecorationsRes apply(GetDecorationsReq req, JsonRequestHandler requestHandler,
			WorkspaceHandler workspaceHandler, LazyParser parser) {
		final DogEnvironment env = new DogEnvironment(requestHandler, workspaceHandler, req.src.src, //
				Parser.parse(LazyParser.extractText(req.src.src, workspaceHandler), '[', ']'), //
				null, false);

		env.printExpectedValuesInComparisonFailures = false;

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

		// Phase 2: Load variables
		for (Container container : env.document.containers) {
			final Probe probe = container.probe();
			if (probe != null && probe.type == Type.VARDECL) {
				final List<RpcBodyLine> rhs = env.evaluateQuery(probe.asVarDecl().src);
				if (rhs != null) {
					addDecoration.add(probe, "var", null);
				}
			}
		}
		if (!env.errMsgs.isEmpty()) {
			env.errMsgs.forEach(errMsg -> {
				addDecoration.add(errMsg.context, "error", errMsg.message);
			});
			int firstVarErr = env.errMsgs.get(0).context.start.line;
			// If the user is looking at a large document with one variable error on one
			// end, they may be confused as to why nothing happens to their normal queries.
			// Therefore, lets decorate all queries with a "skipped" message
			for (Container container : env.document.containers) {
				final Probe probe = container.probe();
				if (probe == null) {
					continue;
				}
				if (probe.type == Type.QUERY) {
					addDecoration.add(probe, "info", "Skipped, see error on line " + firstVarErr);
				}
			}
			return new GetDecorationsRes(ret);
		}

		// Phase 3: Normal queries
		for (Container container : env.document.containers) {
			final Probe probe = container.probe();
			if (probe == null) {
				continue;
			}

			switch (probe.type) {
			case QUERY: {
				final Query query = probe.asQuery();
				final List<RpcBodyLine> lhs = env.evaluateQuery(query);
				if (query.assertion.isPresent()) {
					if (lhs != null) {
						if (env.evaluateComparison(query, lhs)) {
							addDecoration.add(query, "ok", null);
						}
					}
				} else {
					if (lhs != null) {
						final String flat = DogEnvironment.flattenBody(lhs);
						addDecoration.add(probe, "query", "= " + flat);
					}
				}

				break;
			}
			case VARDECL: {
				// Handled above in "phase 2"
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
}
