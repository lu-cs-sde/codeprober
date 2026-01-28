package codeprober.requesthandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import codeprober.ast.AstNode;
import codeprober.locator.AttrsInNode;
import codeprober.locator.Span;
import codeprober.protocol.data.CompleteReq;
import codeprober.protocol.data.CompleteRes;
import codeprober.protocol.data.CompletionItem;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.Property;
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
import codeprober.textprobe.ast.VarDecl;

public class CompleteHandler {

	// Ordinal of these enum entries match the numbers in
	// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItemKind
	private static enum CompletionItemKind {
		INVALID, Text, Method, Function, Constructor, Field, Variable, Class, Interface, Module, Property, Unit, Value,
		Enum, Keyword, Snippet, Color, File, Reference, Folder, EnumMember, Constant, Struct, Event, Operator,
		TypeParameter;
	}

	private static enum SortTextPrefix {
		METAVAR, TYPE, PROPNAME, QUERYRESULT;

		public String getPrefix() {
			switch (this) {
			case QUERYRESULT:
				return "1";
			case PROPNAME:
				return "2";
			case TYPE:
				return "3";
			case METAVAR:
				return "5";
			default:
				System.err.println("Unknown prefix type " + this);
				return "?";
			}
		}
	}

	public static CompleteRes apply(CompleteReq req, JsonRequestHandler reqHandler, LazyParser parser,
			WorkspaceHandler wsHandler) {
		final ParsedAst parsed = parser.parse(req.src);
		final List<String> customComplete = HoverHandler.extract0(parsed, "cpr_ide_complete", req.line, req.column);
		if (customComplete != null) {
			return new CompleteRes(customComplete.stream() //
					.map(x -> new CompletionItem(x, x, CompletionItemKind.Function.ordinal())) //
					.collect(Collectors.toList()));
		}
		if (parsed.info == null) {
			return new CompleteRes();
		}

		// Else, perhaps text probe logic
		final String txt = LazyParser.extractText(req.src.src, wsHandler);
		if (txt != null) {
			final TextProbeEnvironment env = new TextProbeEnvironment(parsed.info, Parser.parse(txt, '[', ']'));
			return completeTextProbes(env, req.line, req.column);
		}

		return new CompleteRes();
	}

	public static CompleteRes completeTextProbes(TextProbeEnvironment env, int line, int column) {
		final CompletionContext compCtx = env.document.completionContextAt(line, column);
		if (compCtx == null) {
			// No completion to be done here
			return new CompleteRes();
		}
		switch (compCtx.type) {
		case QUERY_HEAD_TYPE:
			final Query qhead = compCtx.asQueryHead();
			if (qhead != null && qhead.isArgument()) {
				// Arguments inside other queries cannot be normal types. Only add the metavars
				final List<CompletionItem> ret = new ArrayList<>();
				addMetaVars(env, ret);
				return new CompleteRes(ret);
			}
			return completeTypes(env, line);

		case PROPERTY_NAME:
			final PropertyNameDetail prop = compCtx.asPropertyName();

			final Query subQuery;
			if (prop.access == null) {
				subQuery = prop.query;
			} else {
				int inflatedToIdx = 0;
				final Query srcQuery = prop.query;
				for (PropertyAccess infl : srcQuery.tail) {
					if (infl == prop.access) {
						break;
					}
					++inflatedToIdx;
				}
				if (inflatedToIdx == srcQuery.tail.getNumChild()) {
					subQuery = prop.query;
				} else {
					subQuery = new Query(srcQuery.start, srcQuery.end, srcQuery.head, srcQuery.index,
							srcQuery.tail.toList().subList(0, Math.max(0, inflatedToIdx)));
					srcQuery.adopt(subQuery);
				}
			}
			return completePropAccess(env, subQuery);

		case QUERY_RESULT:
			final QueryResult lhsEval = env.evaluateQuery(compCtx.asQueryResult());
			if (lhsEval == null) {
				return new CompleteRes();
			}
			final List<CompletionItem> ret = new ArrayList<>();
			final String flat = env.flattenBody(lhsEval);
			final String sortText = String.format("%s", SortTextPrefix.QUERYRESULT.getPrefix());
			ret.add(new CompletionItem(flat, flat, CompletionItemKind.Constant.ordinal(), sortText, "Query result"));
			addMetaVars(env, ret);
			return new CompleteRes(ret);

		case VAR_DECL_NAME:
			// Nothing to do
			return new CompleteRes();

		default:
			System.err.println("Unknown completion context: " + compCtx.type);
			return new CompleteRes();
		}
	}

	private static String extractTypeLabel(NodeLocator nl) {
		if (nl.result.label != null) {
			return nl.result.label;
		} else {
			return nl.result.type.substring(nl.result.type.lastIndexOf('.') + 1);
		}
	}

	private static CompleteRes completeTypes(TextProbeEnvironment env, int line) {
		List<NodeLocator> items = new ArrayList<>();
		Map<String, Integer> itemCount = new HashMap<>();

		for (NodeLocator nl : env.listNodes(line, "", String.format("@lineSpan~=%d", line))) {
			items.add(nl);
			String name = extractTypeLabel(nl);
			final int prevCount = itemCount.containsKey(name) ? itemCount.get(name) : 0;
			itemCount.put(name, prevCount + 1);
		}

		List<CompletionItem> ret = new ArrayList<>();
		Map<String, Integer> itemSuffixGen = new HashMap<>();
		for (int idx = 0; idx < items.size(); ++idx) {
			final NodeLocator nl = items.get(idx);
			String item = extractTypeLabel(nl);
			if (itemCount.get(item) > 1) {
				// Need a unique suffix
				final int suffix = itemSuffixGen.containsKey(item) ? itemSuffixGen.get(item) : 0;
				itemSuffixGen.put(item, suffix + 1);
				item = String.format("%s[%d]", item, suffix);
			}
			Integer contextStart, contextEnd;
			final String detail;
			if (nl.result.start == 0 || nl.result.end == 0) {
				contextStart = null;
				contextEnd = null;
				detail = null;
			} else {
				contextStart = nl.result.start;
				contextEnd = nl.result.end;
				detail = String.format("AST node at [%s]",
						new Position(nl.result.start >>> 12, nl.result.start & 0xFFF).toString());
			}

			// Sort in reverse list order -> deepest/narrowest nodes come on top, root nodes
			// on bottom.
			final String sortText = String.format("%s_%03d", SortTextPrefix.TYPE.getPrefix(), items.size() - idx);
			ret.add(new CompletionItem(item, item, CompletionItemKind.Class.ordinal(), //
					sortText, detail, contextStart, contextEnd));
		}
		addMetaVars(env, ret);

		return new CompleteRes(ret);
	}

	private static CompleteRes completePropAccess(TextProbeEnvironment env, Query query) {
		if (!env.document.problems().isEmpty()) {
			// Cannot reliably evaluate queries when there are problems
			return new CompleteRes();
		}
		final QueryResult qres = env.evaluateQuery(query);
		if (qres == null || qres.value == null) {
			return new CompleteRes();
		}

		AstNode subject = null;
		final List<Property> rawAttrs;
		if (env.info.baseAstClazz.isInstance(qres.value)) {
			subject = new AstNode(qres.value);
			rawAttrs = AttrsInNode.getTyped(env.info, subject, AttrsInNode.extractFilter(env.info, subject),
					true /* all */);
		} else {
			rawAttrs = ListPropertiesHandler.extractPropertiesFromNonAstNode(env.info, qres.value);
		}

		Integer remoteContextStart, remoteContextEnd;
		if (subject == null || !subject.getRecoveredSpan(env.info).isMeaningful()) {
			remoteContextStart = null;
			remoteContextEnd = null;
		} else {
			final Span span = subject.getRecoveredSpan(env.info);
			remoteContextStart = span.start;
			remoteContextEnd = span.end;
		}

		int localContextStart = query.head.start.getPackedBits();
		int localContextEnd = (query.tail.isEmpty() //
				? query.head //
				: query.tail.get(query.tail.getNumChild() - 1) //
		).end.getPackedBits();

		return new CompleteRes( //
				rawAttrs.stream() //
						.filter(x -> x.args == null || x.args.stream().allMatch(arg -> {
							switch (arg.type) {
							case any: // a.k.a "Object"
							case integer:
							case string:
								return true;
							default:
								return false;
							}
						})).map(x -> {
							StringBuilder detail = new StringBuilder(x.name);
							if (x.args != null && x.args.size() > 0) {
								detail.append("()");
							}
							String sortText = String.format("%s", SortTextPrefix.PROPNAME.getPrefix());
							return new CompletionItem(x.name, x.name, CompletionItemKind.Function.ordinal(), sortText,
									detail.toString(), remoteContextStart, remoteContextEnd);
						}) //
						.collect(Collectors.toList()), //
				localContextStart, localContextEnd);
	}

	private static void addMetaVars(TextProbeEnvironment env, List<CompletionItem> out) {
		final boolean hasProblems = !env.document.problems().isEmpty();
		int varIdx = 0;
		final Map<String, VarDecl> vdecls = env.document.varDecls();
		for (String metaVar : new TreeSet<>(vdecls.keySet())) {
			final String item = "$" + metaVar;

			final String sortText = String.format("%s_%03d", SortTextPrefix.METAVAR.getPrefix(), ++varIdx);
			final Query srcQuery = vdecls.get(metaVar).src;

			Integer contextStart = null, contextEnd = null;
			final QueryResult evalRes;
			if (hasProblems) {
				// Cannot reliably evaluate queries when problems exist
				evalRes = null;
			} else {
				evalRes = env.evaluateQuery(srcQuery);
			}
			if (evalRes != null && env.info.baseAstClazz.isInstance(evalRes.value)) {
				AstNode node = new AstNode(evalRes.value);
				final Span span = node.getRecoveredSpan(env.info);
				if (span.start != 0 && span.end != 0) {
					contextStart = span.start;
					contextEnd = span.end;
				}
			}
			final String detail = evalRes != null //
					? env.flattenBody(evalRes) //
					: srcQuery.pp();
			out.add(new CompletionItem(item, item, CompletionItemKind.Variable.ordinal(), sortText, detail,
					contextStart, contextEnd));
		}
	}
}
