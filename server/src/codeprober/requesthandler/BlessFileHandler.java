package codeprober.requesthandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import codeprober.protocol.data.BlessFileReq;
import codeprober.protocol.data.BlessFileRes;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.PutWorkspaceContentReq;
import codeprober.protocol.data.PutWorkspaceContentRes;
import codeprober.textprobe.FilteredTextProbeParser;
import codeprober.textprobe.FilteredTextProbeParser.FilteredParse;
import codeprober.textprobe.TextProbeEnvironment;
import codeprober.textprobe.TextProbeEnvironment.QueryResult;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Document;
import codeprober.textprobe.ast.Expr;
import codeprober.textprobe.ast.Position;
import codeprober.textprobe.ast.Probe;
import codeprober.textprobe.ast.Probe.Type;
import codeprober.textprobe.ast.Query;
import codeprober.textprobe.ast.QueryAssert;

public class BlessFileHandler {

	public static BlessFileRes apply(BlessFileReq req, WorkspaceHandler workspaceHandler, LazyParser parser) {
		final String txt = LazyParser.extractText(req.src.src, workspaceHandler);
		if (txt == null) {
			return new BlessFileRes();
		}
		final FilteredParse fp = FilteredTextProbeParser.parse(txt, parser, req.src);
		final Document doc = fp.document;
		if (doc == null) {
			return new BlessFileRes(0);
		}

		if (fp.ast == null) {
			return new BlessFileRes(null, "Failed parsing file");
		}
		final TextProbeEnvironment env = new TextProbeEnvironment(fp.ast.info, doc);

		if (!doc.problems().isEmpty()) {
			return new BlessFileRes(null, encodeMultiLine(doc.problems()));
		}

		final List<PendingUpdate> updates = new ArrayList<>();
		for (Container c : doc.containers) {
			final Probe probe = c.probe();
			if (probe == null || probe.type != Type.QUERY) {
				continue;
			}
			final Query query = probe.asQuery();
			if (!query.assertion.isPresent()) {
				continue;
			}
			final QueryAssert qa = query.assertion();
			if (qa.tilde || qa.exclamation) {
				// Cannot reliably update this
				continue;
			}
			if (qa.expectedValue.type == Expr.Type.QUERY) {
				// Cannot reliably update this, even if the test is failing
				continue;
			}

			final QueryResult lhs = env.evaluateQuery(query);
			if (lhs == null) {
				return new BlessFileRes(null, encodeMultiLine(env.errMsgs));
			}

			if (env.evaluateComparison(query, lhs)) {
				// Passing assertion, nothing to bless
				continue;
			}

			final String lhsStr = env.flattenBody(lhs);
			updates.add(new PendingUpdate( //
					new Position(qa.eq.line, qa.eq.column + 1), //
					new Position(qa.end.line, qa.end.column + 1), //
					qa.expectedValue.pp(), DecorationsHandler.wrapLiteralValueInQuotesIfNecessary(lhsStr)));
		}
		if (updates.isEmpty()) {
			return new BlessFileRes(0);
		}

		switch (req.mode) {
		case DRY_RUN:
			return new BlessFileRes(updates.size(), encodeMultiLine(updates));

		case UPDATE_IN_PLACE:
			if (req.src.src.type != ParsingSource.Type.workspacePath) {
				return new BlessFileRes(null, "Can only 'update in place' workspace paths");
			}
			final PutWorkspaceContentRes putRes = workspaceHandler.handlePutWorkspaceContent(
					new PutWorkspaceContentReq(req.src.src.asWorkspacePath(), apply(txt, updates)));
			if (putRes.ok) {
				return new BlessFileRes(updates.size(), null);
			}
			return new BlessFileRes(null, "Error writing result");

		case ECHO_RESULT:
		default:
			return new BlessFileRes(updates.size(), apply(txt, updates));
		}

	}

	private static String apply(String src, List<PendingUpdate> updates) {
		if (updates.isEmpty())
			return src;

		// Pre-compute line start offsets (1-based lines → 0-based offsets)
		int[] lineOffsets = computeLineOffsets(src);

		StringBuilder sb = new StringBuilder(src.length());
		int cursor = 0; // current offset into src

		for (PendingUpdate update : updates) {
			int startOffset = lineOffsets[update.start.line - 1] + (update.start.column - 1);
			int endOffset = lineOffsets[update.end.line - 1] + (update.end.column - 1);

			// Append unchanged text between previous edit and this one
			sb.append(src, cursor, startOffset);
			// Append the replacement text
			sb.append(update.newText);
			cursor = endOffset;
		}

		// Append remaining text after the last edit
		sb.append(src, cursor, src.length());

		return sb.toString();
	}

	private static int[] computeLineOffsets(String src) {
		// Count lines first
		int lineCount = 1;
		for (int i = 0; i < src.length(); i++) {
			if (src.charAt(i) == '\n')
				lineCount++;
		}

		final int[] offsets = new int[lineCount];
		offsets[0] = 0;
		int line = 1;
		for (int i = 0; i < src.length(); i++) {
			if (src.charAt(i) == '\n') {
				offsets[line++] = i + 1;
			}
		}
		return offsets;
	}

	private static String encodeMultiLine(Collection<?> l) {
		return l.stream().map(x -> x.toString()).collect(Collectors.joining("\n"));
	}

	private static class PendingUpdate {
		public final Position start;
		public final Position end;
		public final String oldText;
		public final String newText;

		public PendingUpdate(Position start, Position end, String oldText, String newText) {
			this.start = start;
			this.end = end;
			this.oldText = oldText;
			this.newText = newText;
		}

		@Override
		public String toString() {
			return String.format("[%s->%s] '%s' -> '%s'", start, end, oldText, newText);
		}
	}
}
