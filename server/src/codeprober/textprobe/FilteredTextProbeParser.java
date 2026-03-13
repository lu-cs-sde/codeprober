package codeprober.textprobe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.Span;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.requesthandler.LazyParser;
import codeprober.requesthandler.LazyParser.ParsedAst;
import codeprober.textprobe.ast.ASTNode;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Document;

public class FilteredTextProbeParser {

	public static boolean filterTextProbesBasedOnASTStructure = !"false"
			.equals(System.getProperty("cpr.filterTextProbesBasedOnASTStructure"));

	public static FilteredParse parse(String txt, LazyParser parser, ParsingRequestData src) {
		return parse(txt, () -> parser.parse(src));
	}

	public static FilteredParse parse(String txt, ParsedAst preparsed) {
		return parse(txt, () -> preparsed);
	}

	public static FilteredParse parse(String txt, Supplier<ParsedAst> parseAst) {
		final Document document = Parser.parse(txt, '[', ']');
		if (document.containers.isEmpty()) {
			return new FilteredParse(null, null);
		}

		final ParsedAst parsedAst = parseAst.get();
		if (parsedAst.info == null) {
			return new FilteredParse(document, null);
		}
		if (!filterTextProbesBasedOnASTStructure) {
			return new FilteredParse(document, parsedAst);
		}

		final Set<Span> containerSpans = new HashSet<>();
		for (Container c : document.containers) {
			containerSpans.add(txtNodeToSpan(c));
		}

		final Set<Span> nodeSpans = new HashSet<>();
		collectOverlappingSpans(parsedAst.info, parsedAst.info.ast, containerSpans, nodeSpans, true);

		final List<Container> filteredContainers = new ArrayList<>();
		final Iterator<Container> iter = document.containers.iterator();
		while (iter.hasNext()) {
			final Container c = iter.next();
			if (!nodeSpans.contains(txtNodeToSpan(c))) {
				filteredContainers.add(c);
			}
		}
		return new FilteredParse(new Document(document.start, document.end, filteredContainers), parsedAst);
	}

	private static Span txtNodeToSpan(ASTNode node) {
		return new Span(node.start.getPackedBits(), node.end.getPackedBits());
	}

	private static void collectOverlappingSpans(AstInfo info, AstNode node, Set<Span> filter, Set<Span> out, boolean checkExternalFiles) {
		if (checkExternalFiles) {
			Boolean extFile = node.isInsideExternalFileRaw(info);
			if (extFile != null) {
				if (!extFile) {
					// Ignore this file
					return;
				}
				// No need to check nodes under this point in the AST
				checkExternalFiles = false;
			}
		}
		final Span span = node.getRawSpan(info);
		if (filter.contains(span)) {
			out.add(span);
		}
		for (AstNode child : node.getChildren(info)) {
			collectOverlappingSpans(info, child, filter, out, checkExternalFiles);
		}
	}

	public static class FilteredParse {
		// If null, then no containers existed
		public final Document document;

		// If null, then no containers existed, or parsing failed
		public final ParsedAst ast;

		public FilteredParse(Document document, ParsedAst ast) {
			this.document = document;
			this.ast = ast;
		}
	}
}
