package codeprober.locator;

import java.util.Objects;

public class Span {
	public final int start, end;

	public Span(int start, int end) {
		this.start = start;
		this.end = end;
	}

	public int getStartLine() {
		return start >>> 12;
	}

	public int getStartColumn() {
		return start & 0xFFF;
	}

	public int getEndLine() {
		return end >>> 12;
	}

	public int getEndColumn() {
		return end & 0xFFF;
	}

	public boolean isMeaningful() {
		return start != 0 || end != 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(end, start);
	}

	@Override
	public String toString() {
		return "[" + start + "," + end + "]";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Span other = (Span) obj;
		return end == other.end && start == other.start;
	}

	public boolean overlaps(Span other) {
		if (end < other.start || start > other.end) {
			return false;
		}
		return true;
	}

	public boolean covers(int startPos, int endPos) {
		return !isMeaningful() || (this.start <= startPos && this.end >= endPos);
	}
}