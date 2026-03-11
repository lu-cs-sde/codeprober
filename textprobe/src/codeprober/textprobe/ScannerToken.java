package codeprober.textprobe;

import codeprober.textprobe.ast.Position;

public class ScannerToken {

	public final ScannerTokenType type;
	public final Position start;
	public final Position end;
	public final Object value;

	public ScannerToken(ScannerTokenType type, Position start, Position end, Object value) {
		this.type = type;
		this.start = start;
		this.end = end;
		this.value = value;
	}

	public String toString() {
		return String.format("[%s->%s] %s%s", start, end, type, (value == null ? "" : String.format(" \"%s\"", value)));
	}
}
