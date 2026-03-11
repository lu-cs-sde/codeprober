package codeprober.textprobe;

public enum ScannerTokenType {
	CONTAINER_OPEN, // "[["
	CONTAINER_CLOSE, // "]]"
	EQ, // "="
	EXCLAMATION, // "!"
	DOLLAR, // "$"
	ASSIGN, // ":="
	CARET, // "^"
	LBRACKET, // "["
	RBRACKET, // "]"
	DOT, // "."
	LPAREN, // "("
	RPAREN, // ")"
	COMMA, // ","
	TILDE, // "~"

	ID, // ID
	NUMBER, // Int literal
	STRING, // String literal
	BOOL, // Boolean literal
	WHITESPACE, // Normal spaces, not nbsp or newlines
}
