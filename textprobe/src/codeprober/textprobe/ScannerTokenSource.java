package codeprober.textprobe;

import java.util.List;

import codeprober.textprobe.ast.Position;

public class ScannerTokenSource {

	public final String src;
	private final char startBracket, endBracket;
	private int offset;
	private int line, column;

	public ScannerTokenSource(String src, char startBracket, char endBracket, int offset, int line, int column) {
		this.src = src;
		this.startBracket = startBracket;
		this.endBracket = endBracket;
		this.offset = offset;
		this.line = line;
		this.column = column;
	}

	public ScannerToken next() {
		if (offset >= src.length()) {
			return null;
		}
		char c = src.charAt(offset);
		if (c == startBracket && peekChar(1) == startBracket) {
			return createSimpleToken(ScannerTokenType.CONTAINER_OPEN, 2);
		} else if (c == endBracket && peekChar(1) == endBracket && peekChar(2) != endBracket) {
			return createSimpleToken(ScannerTokenType.CONTAINER_CLOSE, 2);
		}
		switch (c) {
		case '=':
			return createSimpleToken(ScannerTokenType.EQ, 1);
		case '!': {
			return createSimpleToken(ScannerTokenType.EXCLAMATION, 1);
		}
		case '$':
			return createSimpleToken(ScannerTokenType.DOLLAR, 1);
		case ':':
			if (peekChar(1) == '=') {
				return createSimpleToken(ScannerTokenType.ASSIGN, 2);
			}
			return null;
		case '^':
			return createSimpleToken(ScannerTokenType.CARET, 1);
		case '[':
			return createSimpleToken(ScannerTokenType.LBRACKET, 1);
		case ']':
			return createSimpleToken(ScannerTokenType.RBRACKET, 1);
		case '.':
			return createSimpleToken(ScannerTokenType.DOT, 1);
		case '(':
			return createSimpleToken(ScannerTokenType.LPAREN, 1);
		case ')':
			return createSimpleToken(ScannerTokenType.RPAREN, 1);
		case ',':
			return createSimpleToken(ScannerTokenType.COMMA, 1);
		case '~':
			return createSimpleToken(ScannerTokenType.TILDE, 1);
		case '-': {
			if (isDigit(peekChar(1))) {
				final Position start = new Position(line, column);
				++column;
				++offset;
				final int offsetStart = offset;
				while (isDigit(peekChar(0))) {
					++column;
					++offset;
				}
				int val = -Integer.parseInt(src.substring(offsetStart, offset));
				return new ScannerToken(ScannerTokenType.NUMBER, start, new Position(line, column - 1), val);
			}
			return null;
		}
		case '"': {
			int offsetStart = offset;
			int columnStart = column;
			++column;
			++offset;
			final StringBuilder sb = new StringBuilder();
			while (offset < src.length() && src.charAt(offset) != '"') {
				final char sc = src.charAt(offset++);
				++column;
				if (sc == '\\') {
					if (offset >= src.length()) {
						offset = offsetStart;
						column = columnStart;
						return null; // Trailing backslash before EOF
					}
					char esc = src.charAt(offset++);
					++column;
					switch (esc) {
					case 'n':
						sb.append('\n');
						break;
					case '"':
						sb.append('"');
						break;
					case '\\':
						sb.append('\\');
						break;
					default: {
						offset = offsetStart;
						column = columnStart;
						return null; // Unknown escape sequence
					}
					}
				} else {
					sb.append(sc);
				}
			}
			if (peekChar(0) != '"') {
				offset = offsetStart;
				column = columnStart;
				return null;
			}
			final ScannerToken ret = new ScannerToken(ScannerTokenType.STRING, //
					new Position(line, columnStart + 1), //
					new Position(line, column - 1), sb.toString());
			++offset;
			++column;
			return ret;
		}
		case ' ': {
			final Position start = new Position(line, column);
			++column;
			++offset;
			while (peekChar(0) == ' ') {
				++column;
				++offset;
			}
			return new ScannerToken(ScannerTokenType.WHITESPACE, start, new Position(line, column - 1), null);
		}

		default: {
			if (isDigit(c)) {
				final Position start = new Position(line, column);
				final int offsetStart = offset;
				++column;
				++offset;
				while (isDigit(peekChar(0))) {
					++column;
					++offset;
				}
				int val = Integer.parseInt(src.substring(offsetStart, offset));
				return new ScannerToken(ScannerTokenType.NUMBER, start, new Position(line, column - 1), val);
			} else if (isAlphabetic(c)) {
				final Position start = new Position(line, column);
				final int offsetStart = offset;
				++column;
				++offset;
				while (isIDFollowup(peekChar(0))) {
					++column;
					++offset;
				}

				while (offset > 0 && peekChar(-1) == ':') {
					// Cannot end IDs on a colon
					--column;
					--offset;
				}

				final String contents = src.substring(offsetStart, offset);
				final Position end = new Position(line, column - 1);
				switch (contents) {
				case "true":
					return new ScannerToken(ScannerTokenType.BOOL, start, end, true);
				case "false":
					return new ScannerToken(ScannerTokenType.BOOL, start, end, false);
				case "null":
					return new ScannerToken(ScannerTokenType.STRING, start, end, contents);
				default:
					return new ScannerToken(ScannerTokenType.ID, start, end, contents);
				}
			}
			return null;
		}
		}
	}

	private static boolean isIDFollowup(char c) {
		if (isDigit(c) || isAlphabetic(c)) {
			return true;
		}
		switch (c) {
		case ':':
		case '_':
			return true;
		}
		return false;
	}

	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private static boolean isAlphabetic(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}

	private char peekChar(int extraOffset) {
		final int pos = offset + extraOffset;
		if (pos < src.length()) {
			return src.charAt(pos);
		}
		return '§'; // Unused character as fallback
	}

	private ScannerToken createSimpleToken(ScannerTokenType t, int characterWidth) {
		Position start = new Position(line, column);
		column += characterWidth;
		offset += characterWidth;
		Position end = new Position(line, column - 1);
		return new ScannerToken(t, start, end, null);
	}

//
	public void setOffset(int offset) {
		this.offset = offset;
	}

	public int getOffset() {
		return offset;
	}

	public void skipUntilClosing() {
		while (offset < src.length() - 1
				&& !(peekChar(0) == endBracket && peekChar(1) == endBracket && peekChar(2) != endBracket)) {
			++offset;
			++column;
		}
	}

	public enum StoppingPoint {
		EOF, CLOSING, IMPLICIT_STRING
	}

	public StoppingPoint scanUntilDecisionPointWithoutImplicitString(List<ScannerToken> out) {
		ScannerToken tok;
		while ((tok = next()) != null) {
			out.add(tok);
			if (tok.type == ScannerTokenType.CONTAINER_CLOSE) {
				return StoppingPoint.CLOSING;
			}
		}
		return StoppingPoint.EOF;
	}

	public StoppingPoint scanUntilDecisionPoint(List<ScannerToken> out) {
		if (!Parser.permitImplicitStringConversion) {
			// Easy: just scan until EOL or closing line
			return scanUntilDecisionPointWithoutImplicitString(out);
		}
		// Else, more tricky. If comparison query, may need auto-string boxing on
		// right-hand side

		ScannerToken tok;
		while ((tok = next()) != null) {
			out.add(tok);
			switch (tok.type) {
			case CONTAINER_CLOSE:
				return StoppingPoint.CLOSING;
			case ASSIGN:
				// This is a var assignment, not a comparison
				// Defer to the simpler scanning method
				return scanUntilDecisionPointWithoutImplicitString(out);
			case EQ: {
				return StoppingPoint.IMPLICIT_STRING;
			}
			default:
				break;
			}
		}
		return StoppingPoint.EOF;
	}
}
