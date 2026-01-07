package codeprober.textprobe;

import codeprober.textprobe.ast.Label;
import codeprober.textprobe.ast.Position;

public class ParserSource {

	private final String src;
	private int offset = 0;

	public ParserSource(String src) {
		this.src = src;
	}

	// Get the current offset
	public int getOffset() {
		return offset;
	}

	// Set the offset to a specific position
	public void setOffset(int offset) {
		this.offset = offset;
	}

	// Check if we've reached the end of the source
	public boolean isEOF() {
		return offset >= src.length();
	}

	// Peek at the current character without consuming it
	public char peek() {
		if (isEOF()) {
			return '\0';
		}
		return src.charAt(offset);
	}

	// Peek at a character at a specific offset ahead
	public char peek(int ahead) {
		int pos = offset + ahead;
		if (pos >= src.length()) {
			return '\0';
		}
		return src.charAt(pos);
	}

	// Skip any whitespace at the current offset
	public void skipWS() {
		while (offset < src.length() && Character.isWhitespace(src.charAt(offset))) {
			offset++;
		}
	}

	public Label parseLabel(int line, int baseColumn) {
		int start = offset;
		String value = parseID();
		if (value == null) {
			return null;
		}
		return new Label(new Position(line, baseColumn + start), new Position(line, baseColumn + offset - 1), value);
	}

	// Parse the next ID in the stream, return it and advance the offset, or return
	// null if no such ID exists
	public String parseID() {
		if (isEOF()) {
			return null;
		}

		int start = offset;

		// First character must be a valid identifier start (letter or underscore)
		if (!isValidIDStart(src.charAt(offset))) {
			return null;
		}
		offset++;

		// Remaining characters can be letters, digits, or underscores
		while (offset < src.length() && isValidIDFollowup(src.charAt(offset))) {
			offset++;
		}

		return src.substring(start, offset);
	}

	private static boolean isValidIDStart(char c) {
		if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
			return true;
		}
		if (c == '$') {
			return true;
		}
		return false;
	}

	private static boolean isValidIDFollowup(char c) {
		if (isValidIDStart(c)) {
			return true;
		}
		switch (c) {
		case '_':
			return true;
		default:
			return false;
		}
	}

	// Similar as parseID, but parse an integer.
	public Integer parseInt() {
		if (isEOF()) {
			return null;
		}

		int start = offset;

		// Check for optional minus sign
		if (src.charAt(offset) == '-') {
			offset++;
			if (isEOF() || !Character.isDigit(src.charAt(offset))) {
				offset = start; // Reset
				return null;
			}
		}

		// Must have at least one digit
		if (!Character.isDigit(src.charAt(offset))) {
			offset = start; // Reset
			return null;
		}

		// Parse digits
		while (offset < src.length() && Character.isDigit(src.charAt(offset))) {
			offset++;
		}

		String numStr = src.substring(start, offset);
		try {
			return Integer.parseInt(numStr);
		} catch (NumberFormatException e) {
			offset = start; // Reset
			return null;
		}
	}

	// Parse a quoted string (returns the content without quotes)
	public String parseQuotedString() {
		if (isEOF() || src.charAt(offset) != '"') {
			return null;
		}

		offset++; // Skip opening quote
		int start = offset;

		// Find closing quote
		while (offset < src.length() && src.charAt(offset) != '"') {
			// TODO: Handle escape sequences if needed
			offset++;
		}

		if (isEOF()) {
			return null; // Unterminated string
		}

		String str = src.substring(start, offset);
		offset++; // Skip closing quote
		return str;
	}

	// Try to accept a specific character, consuming it if present
	public boolean accept(char c) {
		if (offset < src.length() && src.charAt(offset) == c) {
			++offset;
			return true;
		}
		return false;
	}

	// Try to accept a specific string, consuming it if present
	public boolean accept(String s) {
		if (offset + s.length() <= src.length() && src.substring(offset, offset + s.length()).equals(s)) {
			offset += s.length();
			return true;
		}
		return false;
	}

	// Get remaining content from current offset
	public String remaining() {
		if (isEOF()) {
			return "";
		}
		return src.substring(offset);
	}

	// Get a substring from start to current offset
	public String substring(int start) {
		return src.substring(start, offset);
	}

	// Get a substring from start to end
	public String substring(int start, int end) {
		return src.substring(start, end);
	}

	// Save current position
	public int mark() {
		return offset;
	}

	// Restore to a saved position
	public void reset(int mark) {
		offset = mark;
	}
}
