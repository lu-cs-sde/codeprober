package pasta.locator;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import pasta.metaprogramming.Reflect;
import pasta.protocol.PositionRecoveryStrategy;

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

	static Span extractPositionDownwards(Object astNode) throws NoSuchMethodException, InvocationTargetException {
		final Span ownPos = from(astNode);
		if (!ownPos.isMeaningful()) {
			final Object child = Reflect.getFirstChild(astNode);
			if (child != null) {
				return extractPositionDownwards(child);
			}
		}
		return ownPos;
	}

	static Span extractPositionUpwards(Object astNode) throws NoSuchMethodException, InvocationTargetException {
		final Span ownPos = from(astNode);
		if (!ownPos.isMeaningful()) {
			final Object parent = Reflect.throwingInvoke0(astNode, "getParent");
			if (parent != null) {
				return extractPositionUpwards(parent);
			}
		}
		return ownPos;
	}

	public static Span extractPosition(Object astNode, PositionRecoveryStrategy recoveryStrategy)
			throws NoSuchMethodException, InvocationTargetException {
		final Span ownPos = new Span((Integer) Reflect.throwingInvoke0(astNode, "getStart"),
				(Integer) Reflect.throwingInvoke0(astNode, "getEnd"));
		if (ownPos.isMeaningful()) {
			return ownPos;
		}
		switch (recoveryStrategy) {
		case FAIL:
			return ownPos;
		case PARENT:
			return Span.extractPositionUpwards(astNode);
		case CHILD:
			return Span.extractPositionDownwards(astNode);
		case SEQUENCE_PARENT_CHILD: {
			final Span parent = Span.extractPositionUpwards(astNode);
			return parent.isMeaningful() ? parent : Span.extractPositionDownwards(astNode);
		}
		case SEQUENCE_CHILD_PARENT: {
			final Span parent = Span.extractPositionDownwards(astNode);
			return parent.isMeaningful() ? parent : Span.extractPositionUpwards(astNode);
		}
		case ALTERNATE_PARENT_CHILD: {
			Object parent = astNode;
			Object child = astNode;
			while (parent != null || child != null) {
				if (parent != null) {
					parent = Reflect.getParent(parent);
					if (parent != null) {
						final Span parPos = from(parent);
						if (parPos.isMeaningful()) {
							return parPos;
						}
					}
				}
				if (child != null) {
					child = Reflect.getFirstChild(child);
					if (child != null) {
						final Span childPos = from(child);
						if (childPos.isMeaningful()) {
							return childPos;
						}
					}
				}
			}
			return ownPos;
		}
		default: {
			System.err.println("Unknown recovery strategy " + recoveryStrategy);
			return ownPos;
		}
		}
	}

	static Span from(Object astNode) throws NoSuchMethodException, InvocationTargetException {
		return new Span((Integer) Reflect.throwingInvoke0(astNode, "getStart"),
				(Integer) Reflect.throwingInvoke0(astNode, "getEnd"));
	}
}