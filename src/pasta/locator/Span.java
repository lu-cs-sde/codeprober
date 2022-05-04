package pasta.locator;

import java.util.Objects;

import pasta.AstInfo;
import pasta.ast.AstNode;
import pasta.metaprogramming.InvokeProblem;

public class Span {
	public final int start, end;

	public Span(int start, int end) {
		this.start = start;
		this.end = end;
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
		return "[" + start +"," + end + "]";
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

	static Span extractPositionDownwards(AstInfo info, AstNode astNode) {
		final Span ownPos = astNode.getRawSpan(info);
		if (!ownPos.isMeaningful()) {
			final AstNode child = astNode.getNthChild(0);
			if (child != null) {
				return extractPositionDownwards(info, child);
			}
		}
		return ownPos;
	}

	static Span extractPositionUpwards(AstInfo info, AstNode astNode) {
		final Span ownPos = astNode.getRawSpan(info);
		if (!ownPos.isMeaningful()) {
			final AstNode parent = astNode.parent();
			if (parent != null) {
				return extractPositionUpwards(info, parent);
			}
		}
		return ownPos;
	}

	public static Span extractPosition(AstInfo info, AstNode astNode)
			throws InvokeProblem {
		final Span ownPos = astNode.getRawSpan(info);
		if (ownPos.isMeaningful()) {
			return ownPos;
		}
		switch (info.recoveryStrategy) {
		case FAIL:
			return ownPos;
		case PARENT:
			return Span.extractPositionUpwards(info, astNode);
		case CHILD:
			return Span.extractPositionDownwards(info, astNode);
		case SEQUENCE_PARENT_CHILD: {
			final Span parent = Span.extractPositionUpwards(info, astNode);
			return parent.isMeaningful() ? parent : Span.extractPositionDownwards(info, astNode);
		}
		case SEQUENCE_CHILD_PARENT: {
			final Span parent = Span.extractPositionDownwards(info, astNode);
			return parent.isMeaningful() ? parent : Span.extractPositionUpwards(info, astNode);
		}
		case ALTERNATE_PARENT_CHILD: {
			AstNode parent = astNode;
			AstNode child = parent;
			while (parent != null || child != null) {
				if (parent != null) {
					parent = parent.parent();
					if (parent != null) {
						final Span parPos = parent.getRawSpan(info);
						if (parPos.isMeaningful()) {
							return parPos;
						}
					}
				}
				if (child != null) {
					child = child.getNumChildren() > 0 ? child.getNthChild(0) : null;
					if (child != null) {
						final Span childPos = child.getRawSpan(info);
						if (childPos.isMeaningful()) {
							return childPos;
						}
					}
				}
			}
			return ownPos;
		}
		default: {
			System.err.println("Unknown recovery strategy " + info.recoveryStrategy);
			return ownPos;
		}
		}
	}
}