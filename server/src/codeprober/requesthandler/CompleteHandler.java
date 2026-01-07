package codeprober.requesthandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import codeprober.ast.AstNode;
import codeprober.locator.ApplyLocator;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.locator.AttrsInNode;
import codeprober.locator.Span;
import codeprober.protocol.data.CompleteReq;
import codeprober.protocol.data.CompleteRes;
import codeprober.protocol.data.CompletionItem;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.Property;
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

		// Else, perhaps text probe logic
		final String txt = LazyParser.extractText(req.src.src, wsHandler);
		if (txt != null) {
			final DogEnvironment env = new DogEnvironment(reqHandler, wsHandler, req.src.src,
					Parser.parse(txt, '[', ']'), null, false);
			final CompletionContext compCtx = env.document.completionContextAt(req.line, req.column);
			if (compCtx == null) {
				// No completion to be done here
				return new CompleteRes();
			}
			switch (compCtx.type) {
			case QUERY_HEAD_TYPE:
				return completeTypes(req, parsed, env);

			case PROPERTY_NAME:
				final PropertyNameDetail prop = compCtx.asPropertyName();

				final Query inflated = prop.query.inflate();
				int inflatedToIdx;
				if (prop.access == null) {
					inflatedToIdx = inflated.tail.getNumChild();
				} else {
					inflatedToIdx = -1;
					for (PropertyAccess infl : inflated.tail) {
						++inflatedToIdx;
						if (infl == prop.access) {
							break;
						}
					}
				}
				return completePropAccess(req, parsed, env, prop.query, inflated, prop.access, inflatedToIdx);

			case QUERY_RESULT:
				final List<RpcBodyLine> lhsEval = env.evaluateQuery(compCtx.asQueryResult());
				if (lhsEval == null) {
					return new CompleteRes();
				}
				final List<CompletionItem> ret = new ArrayList<>();
				final String flat = DogEnvironment.flattenBody(lhsEval);
				final String sortText = String.format("%s", SortTextPrefix.QUERYRESULT.getPrefix());
				ret.add(new CompletionItem(flat, flat, CompletionItemKind.Constant.ordinal(), sortText,
						"Query result"));
				addMetaVars(env, ret);
				return new CompleteRes(ret);

			default:
				System.err.println("Unknown completion context: " + compCtx.type);
				return new CompleteRes();
			}
		}

		return new CompleteRes();
	}

	private static String extractTypeLabel(NodeLocator nl) {
		if (nl.result.label != null) {
			return nl.result.label;
		} else {
			return nl.result.type.substring(nl.result.type.lastIndexOf('.') + 1);
		}
	}

	private static CompleteRes completeTypes(CompleteReq req, ParsedAst parsed, DogEnvironment env) {
		List<NodeLocator> items = new ArrayList<>();
		Map<String, Integer> itemCount = new HashMap<>();

		for (NodeLocator nl : env.listNodes(req.line, "", String.format("@lineSpan~=%d", req.line))) {
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

	private static CompleteRes completePropAccess(CompleteReq req, ParsedAst parsed, DogEnvironment env,
			Query preinflated, Query inflated, PropertyAccess mainAccess, int tailIdx) {
		final NodeLocator loc = env.evaluateQueryHead(inflated.head, inflated.index);
		if (loc == null) {
			return new CompleteRes();
		}

		ResolvedNode match = ApplyLocator.toNode(parsed.info, loc);
		if (match == null || match.node == null) {
			return new CompleteRes();
		}

		final List<PropertyAccess> subAccesses = inflated.tail.toList().subList(0, tailIdx);
		final List<String> attrs = subAccesses.stream().map(x -> x.name.value).collect(Collectors.toList());
		final Object result = ListPropertiesHandler.evaluateAttrChain(parsed.info, match.node.underlyingAstNode, attrs,
				env.mapPropAccessesToArgLists(subAccesses), new ArrayList<>());
		if (result == null || result == ListPropertiesHandler.ATTR_CHAIN_FAILED) {
			return new CompleteRes();
		}
		final List<Property> rawAttrs;
		// The subject (owner) of the property may not be the same as the query head
		// node.
		final AstNode subject;
		if (parsed.info.baseAstClazz.isInstance(result)) {
			subject = new AstNode(result);
			rawAttrs = AttrsInNode.getTyped(parsed.info, subject, AttrsInNode.extractFilter(parsed.info, match.node),
					true /* all */);
		} else {
			rawAttrs = ListPropertiesHandler.extractPropertiesFromNonAstNode(parsed.info, result);
			subject = null;
		}

		Integer remoteContextStart, remoteContextEnd;
		if (subject == null || !subject.getRecoveredSpan(parsed.info).isMeaningful()) {
			remoteContextStart = null;
			remoteContextEnd = null;
		} else {
			final Span span = subject.getRecoveredSpan(parsed.info);
			remoteContextStart = span.start;
			remoteContextEnd = span.end;
		}

		int preinflatedIdx = -2;
		for (PropertyAccess acc : preinflated.tail) {
			++preinflatedIdx;
			if (acc == mainAccess) {
				break;
			}
		}
		int localContextStart = preinflated.head.start.getPackedBits();
		int localContextEnd = ((preinflatedIdx < 0 || preinflatedIdx >= preinflated.tail.getNumChild())
				? preinflated.head //
				: preinflated.tail.get(preinflatedIdx) //
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

	private static void addMetaVars(DogEnvironment env, List<CompletionItem> out) {
		int varIdx = 0;
		final Map<String, VarDecl> vdecls = env.document.varDecls();
		for (String metaVar : new TreeSet<>(vdecls.keySet())) {
			final String item = "$" + metaVar;

			final String sortText = String.format("%s_%03d", SortTextPrefix.METAVAR.getPrefix(), ++varIdx);
			final Query srcQuery = vdecls.get(metaVar).src;

			Integer contextStart = null, contextEnd = null;
			final List<RpcBodyLine> evalRes = env.evaluateQuery(srcQuery);
			if (evalRes != null && evalRes.size() >= 1 && evalRes.get(0).isNode()) {
				final NodeLocator node = evalRes.get(0).asNode();
				if (node.result.start != 0 && node.result.end != 0) {
					contextStart = node.result.start;
					contextEnd = node.result.end;
				}
			}

			final String detail = (evalRes != null && !evalRes.isEmpty()) //
					? DogEnvironment.flattenBody(evalRes) //
					: srcQuery.pp();
			out.add(new CompletionItem(item, item, CompletionItemKind.Variable.ordinal(), sortText, detail,
					contextStart, contextEnd));
		}

	}
}
