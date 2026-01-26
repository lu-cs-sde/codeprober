package codeprober.textprobe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import codeprober.textprobe.ast.Argument;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Document;
import codeprober.textprobe.ast.ExpectedValue;
import codeprober.textprobe.ast.Label;
import codeprober.textprobe.ast.Position;
import codeprober.textprobe.ast.Probe;
import codeprober.textprobe.ast.PropertyAccess;
import codeprober.textprobe.ast.Query;
import codeprober.textprobe.ast.QueryAssert;
import codeprober.textprobe.ast.QueryHead;
import codeprober.textprobe.ast.VarDecl;
import codeprober.textprobe.ast.VarUse;

public class Parser {

	/**
	 * Parse all text probes in the given strings. Parsing can never fail - for
	 * unexpected inputs, this simply returns a document with zero
	 * containers/probes.
	 * <p>
	 * The document contains a number of containers, which represent "[[", "]]", and
	 * the content in between (or different brackets depending on how
	 * <code>startBracket</code> and <code>endBracket</code> is specified. Each
	 * container in turn contains zero or one probes (see
	 * {@link Container#probe()}). If the probe is <code>null</code>, then it means
	 * that the content inside the container is empty, or of an unexpected syntax.
	 * Handling empty/unexpected syntax can be important for language services
	 * (completion/hover/etc).
	 *
	 */
	public static Document parse(String s, char startBracket, char endBracket) {
		final String[] lines = s.split("\n");
		List<Container> containers = new ArrayList<>();

		for (int lineIdx = 0; lineIdx < lines.length; ++lineIdx) {
			final String line = lines[lineIdx];
			final int lineNumber = lineIdx + 1; // 1-based line numbers

			int col = 0;
			while (col < line.length()) {
				// Check for container start
				if (col + 1 < line.length() && line.charAt(col) == startBracket
						&& line.charAt(col + 1) == startBracket) {
					int startCol = col + 1; // 1-based column
					int contentStart = col + 2;

					// Find the end
					int endPos = -1;
					for (int i = contentStart; i + 1 < line.length(); i++) {
						if (line.charAt(i) == endBracket && line.charAt(i + 1) == endBracket) {
							// Found potential end, but check for additional closing brackets first
							endPos = i + 2;
							while (endPos < line.length() && line.charAt(endPos) == endBracket) {
								++endPos;
							}
							break;
						}
					}

					// Only create container if we found a valid end
					if (endPos != -1) {
						final Position start = new Position(lineNumber, startCol);
						final Position end = new Position(lineNumber, endPos);
						final String content = line.substring(startCol + 1, endPos - 2);
						containers.add(new Container(start, end, content, parseProbe(start, end, content)));
						col = endPos; // Continue after the closing brackets (endPos already points past them)
					} else {
						// No valid end found, can skip the entire rest of the line
						break;
					}
				} else {
					col++;
				}
			}
		}

		return new Document(new Position(1, 1), // Start
				new Position(lines.length + 1, 1), // End
				containers);
	}

	private static Probe parseProbe(Position start, Position end, String content) {
		// Multiple styles of probes are possible
		// Try parsing each one in turn
		ParserSource src = new ParserSource(content);

		Position contentStart = new Position(start.line, start.column + 2);
		QueryParseResult queryRes = parseQueryPattern(contentStart, src);
		if (queryRes != null) {
			if (src.isEOF()) {
				final Position contentEnd = new Position(end.line, end.column - 2);
				return Probe
						.fromQuery(new Query(contentStart, contentEnd, queryRes.head, queryRes.index, queryRes.tail));
			} else {
				// Not EOF, maybe an assertion?
				final Query assertQuery = parseAssertQuery(start, contentStart, end, queryRes, src);
				if (assertQuery != null) {
					return Probe.fromQuery(assertQuery);
				}
			}
		}
		// Reset for next parse attempt
		src.reset(0);

		// Try to parse as VarDecl.
		VarDecl varDecl = parseVarDecl(start, end, src);
		if (varDecl != null) {
			return Probe.fromVarDecl(varDecl);
		}

		// Neither pattern worked, just return null
		return null;
	}

	// Helper class to hold the result of parsing a query pattern
	private static class QueryParseResult {
		final QueryHead head;
		final Integer index;
		final List<PropertyAccess> tail;
		final int endOffset; // Offset in content where parsing stopped

		QueryParseResult(QueryHead head, Integer index, List<PropertyAccess> tail, int endOffset) {
			this.head = head;
			this.index = index;
			this.tail = tail;
			this.endOffset = endOffset;
		}
	}

	// Parse as many dot-separated IDs as possible, returning the labels and where
	// parsing stopped
	// The 'start' position should point to where the content begins (after [[)
	private static QueryParseResult parseQueryPattern(Position start, ParserSource src) {
		List<PropertyAccess> accesses = new ArrayList<>();
		Integer index = null;

		while (!src.isEOF()) {
			// Check if we're at a dot (if we already have labels)
			if (!accesses.isEmpty()) {
				if (!src.accept('.')) {
					// No more dots, stop parsing
					break;
				}
			}

			// Try to parse an identifier
			Label label = src.parseLabel(start.line, start.column);
			if (label == null) {
				if (!accesses.isEmpty() && (src.isEOF() || src.peek() == '=')) {
					// The sytax is something like:
					// [[A.b.]]
					// The user has likely just typed the dot and would like a list of completion
					// options. Therefore we'll treat a trailing dot as valid syntax that is ignored
					// Break out and return as normal
					break;
				}
				return null;
			}
			Position accessEnd = label.end;
			List<Argument> args = null;
			Position argStart = null;

			if (accesses.isEmpty()) {
				// First label may be followed by an indexing step
				int save = src.mark();
				if (src.accept('[')) {
					Integer idx = src.parseInt();
					if (idx != null && src.accept(']')) {
						index = idx;
						accessEnd = new Position(start.line, start.column + src.getOffset() - 1);
					}
				}
				if (index == null) {
					src.reset(save);
				}
			}

			// Labels may be followed by parentheses to turn them into function calls
			if (src.accept('(')) {
				args = new ArrayList<>();
				argStart = new Position(start.line, start.column + src.getOffset() - 1);
				src.skipWS();
				while (!src.isEOF() && src.peek() != ')') {
					// Parse an argument
					Argument arg = null;

					int argOffset = src.getOffset();
					final char peek = src.peek();
					switch (peek) {
					case '"': {
						String litStr = src.parseQuotedString();
						if (litStr == null) {
							return null;
						}
						Position strStart = new Position(start.line, start.column + argOffset + 1);
						Position strEnd = new Position(start.line, start.column + src.getOffset() - 2);
						arg = new Argument(new Label(strStart, strEnd, litStr));
						break;
					}
					case '$': {
						// Parse a Query argument
						int queryStart = src.getOffset();
						QueryParseResult queryResult = parseQueryPattern(start, src);
						if (queryResult == null) {
							return null; // Invalid query
						}
						final Position queryStartPos = new Position(start.line, start.column + queryStart);
						final Position queryEndPos = new Position(start.line, start.column + src.getOffset() - 1);
						final Query query = new Query(queryStartPos, queryEndPos, queryResult.head, queryResult.index,
								queryResult.tail);
						arg = new Argument(query);
						break;
					}
					default: {
						if (peek >= '0' && peek <= '9' || peek == '-') {
							Integer litNum = src.parseInt();
							if (litNum == null) {
								return null;
							}
							Position numStart = new Position(start.line, start.column + argOffset);
							Position numEnd = new Position(start.line, start.column + src.getOffset() - 1);
							arg = new Argument(numStart, numEnd, litNum);
						} else {
							return null;
						}
						break;
					}
					}

					if (arg != null) {
						args.add(arg);
					}

					src.skipWS();
					if (src.peek() == ',') {
						src.accept(',');
						src.skipWS();
					} else if (src.peek() == ')') {
						break;
					} else {
						return null; // Unexpected character
					}
				}

				if (!src.accept(')')) {
					return null; // Missing closing paren
				}
				accessEnd = new Position(start.line, start.column + src.getOffset() - 1);
			}

			accesses.add(new PropertyAccess(label.start, accessEnd, label, argStart, args));
		}

		if (accesses.isEmpty()) {
			return null;
		}

		PropertyAccess firstAccess = accesses.get(0);
		Label head = firstAccess.name;
		if (head.value.startsWith("$")) {
			return new QueryParseResult( //
					QueryHead.fromVar(new VarUse(head.start, firstAccess.end, head.value.substring(1))), //
					index, //
					accesses.subList(1, accesses.size()), //
					src.getOffset());
		}
		return new QueryParseResult( //
				QueryHead.fromType(new Label(head.start, firstAccess.end, head.value)), //
				index, //
				accesses.subList(1, accesses.size()), //
				src.getOffset());
	}

	private static Query parseAssertQuery(Position containerStart, Position contentStart, Position end,
			QueryParseResult queryRes, ParserSource src) {
		// Parse pattern: ID (. ID)* [!][~]= val
		// Where val can be any string (including empty) or a Query starting with $

		final Position eqPos = new Position(contentStart.line, contentStart.column + src.getOffset());
		final Position contentEnd = new Position(end.line, end.column - 2);
		// Check for optional ! and ~ modifiers
		boolean exclamation = src.accept('!');
		boolean tilde = src.accept('~');

		// Must have equals sign
		if (!src.accept('=')) {
			if (src.isEOF() && exclamation && !tilde) {
				// Ending with "!" is syntax sugar for "!=false"
				return new Query(contentStart, contentEnd, queryRes.head, queryRes.index, queryRes.tail,
						new QueryAssert(eqPos, eqPos, true, false, eqPos,
								ExpectedValue.fromConstant(new Label(eqPos, eqPos, "false"))));
			}
			return null; // No equals sign, not an AssertQuery
		}

		// Rest of content is the expected value
		int valueOffset = src.getOffset();
		String valuePart = src.remaining();
		Position valueStart = new Position(contentStart.line, contentStart.column + valueOffset);
		Position valueEnd = new Position(contentStart.line, contentStart.column + valueOffset + valuePart.length() - 1);

		ExpectedValue expectedValue;

		// Check if the expected value starts with $ (QueryExpectedValue)
		if (valuePart.startsWith("$")) {
			// Parse as a Query
			ParserSource valueSrc = new ParserSource(valuePart);
			QueryParseResult queryResult = parseQueryPattern(valueStart, valueSrc);
			if (queryResult == null || queryResult.endOffset < valuePart.length()) {
				return null; // Invalid query or extra content
			}
			Query query = new Query(valueStart, valueEnd, queryResult.head, queryResult.index, queryResult.tail);
			expectedValue = ExpectedValue.fromQuery(query);
		} else {
			// Parse as a constant (Label)
			Label valueLabel = new Label(valueStart, valueEnd, valuePart);
			expectedValue = ExpectedValue.fromConstant(valueLabel);
		}

		// Calculate content positions for AssertQuery
		return new Query(contentStart, contentEnd, queryRes.head, queryRes.index, queryRes.tail,
				new QueryAssert(eqPos, contentEnd, exclamation, tilde, eqPos, expectedValue));
	}

	private static VarDecl parseVarDecl(Position start, Position end, ParserSource src) {
		// Parse pattern: $ID:=Query
		// No spaces permitted between components

		// Must start with $
		if (!src.accept('$')) {
			return null;
		}

		// Parse the variable name (ID)
		int nameStart = src.getOffset() - 1; // -1 for '$'
		String varName = src.parseID();
		if (varName == null) {
			return null; // No identifier found
		}

		// Create the name label
		Position nameStartPos = new Position(start.line, start.column + 2 + nameStart);
		Position nameEndPos = new Position(start.line, start.column + 2 + src.getOffset() - 1);
		Label nameLabel = new Label(nameStartPos, nameEndPos, varName);

		// Must have ":=" next
		if (!src.accept(":=")) {
			return null; // No := found
		}

		final Position contentStart = new Position(start.line, start.column + 2);
		final Position contentEnd = new Position(end.line, end.column - 2);

		// Rest of content must be a valid Query
		int queryOffset = src.getOffset();
		String queryContent = src.remaining();
		if (queryContent.isEmpty()) {
			// Situation: [[$name:=]]
			// Treat this as a query with empty type label
			final Position queryLoc = contentEnd; // new Position(start.line, start.column + 2 + queryOffset);
			Query srcQuery = new Query(queryLoc, queryLoc, QueryHead.fromType(new Label(queryLoc, queryLoc, "")), null,
					Collections.emptyList());
			return new VarDecl(contentStart, contentEnd, nameLabel, srcQuery);
		}

		// Create adjusted positions for the query part
		Position queryStart = new Position(start.line, start.column + 2 + queryOffset);
		Position queryEnd = new Position(start.line, start.column + 2 + queryOffset + queryContent.length() - 1);

		// Parse the query part
		ParserSource querySrc = new ParserSource(queryContent);
		QueryParseResult queryResult = parseQueryPattern(queryStart, querySrc);
		if (queryResult == null || !querySrc.isEOF()) {
			return null; // Invalid query or extra content after query
		}

		// Create the Query object for the src
		Query srcQuery = new Query(queryStart, queryEnd, queryResult.head, queryResult.index, queryResult.tail);

		return new VarDecl(contentStart, contentEnd, nameLabel, srcQuery);
	}
}
