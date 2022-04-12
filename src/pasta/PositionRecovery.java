package pasta;

import java.lang.reflect.InvocationTargetException;

public class PositionRecovery {

	static Position extractPosition(Object astNode, PositionRecoveryStrategy recoveryStrategy)
			throws NoSuchMethodException, InvocationTargetException {
		final Position ownPos = new Position((Integer) Reflect.throwingInvoke0(astNode, "getStart"),
				(Integer) Reflect.throwingInvoke0(astNode, "getEnd"));
		if (ownPos.isMeaningful()) {
			return ownPos;
		}
		switch (recoveryStrategy) {
		case FAIL:
			return ownPos;
		case PARENT:
			return extractPositionUpwards(astNode);
		case CHILD:
			return PositionRecovery.extractPositionDownwards(astNode);
		case SEQUENCE_PARENT_CHILD: {
			final Position parent = extractPositionUpwards(astNode);
			return parent.isMeaningful() ? parent : extractPositionDownwards(astNode);
		}
		case SEQUENCE_CHILD_PARENT: {
			final Position parent = extractPositionDownwards(astNode);
			return parent.isMeaningful() ? parent : extractPositionUpwards(astNode);
		}
		case ALTERNATE_PARENT_CHILD: {
			Object parent = astNode;
			Object child = astNode;
			while (parent != null || child != null) {
				if (parent != null) {
					parent = Reflect.getParent(parent);
					if (parent != null) {
						final Position parPos = Position.from(parent);
						if (parPos.isMeaningful()) {
							return parPos;
						}
					}
				}
				if (child != null) {
					child = Reflect.getFirstChild(child);
					if (child != null) {
						final Position childPos = Position.from(child);
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

	static Position extractPositionUpwards(Object astNode)
			throws NoSuchMethodException, InvocationTargetException {
		final Position ownPos = Position.from(astNode);
		if (!ownPos.isMeaningful()) {
			final Object parent = Reflect.throwingInvoke0(astNode, "getParent");
			if (parent != null) {
				return extractPositionUpwards(parent);
			}
		}
		return ownPos;
	}

	static Position extractPositionDownwards(Object astNode)
			throws NoSuchMethodException, InvocationTargetException {
		final Position ownPos = Position.from(astNode);
		if (!ownPos.isMeaningful()) {
			final Object child = Reflect.getFirstChild(astNode);
			if (child != null) {
				return extractPositionDownwards(child);
			}
		}
		return ownPos;
	}

}
