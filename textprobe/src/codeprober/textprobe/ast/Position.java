package codeprober.textprobe.ast;

public class Position {
	public final int line;
	public final int column;

	public Position(int line, int column) {
		this.line = line;
		this.column = column;
	}

	public String toString() {
		return String.format("%d:%d", line, column);
	}

	public int getPackedBits() {
		return (line << 12) + column;
	}

	// For unpositioned nodes
	public static final Position NIL = new Position(0, 0);
}
