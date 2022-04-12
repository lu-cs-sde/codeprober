package pasta;

import java.lang.reflect.InvocationTargetException;

public class TypeAtLoc {

	public final String type;
	public final Position position;

	public TypeAtLoc(String type, Position position) {
		this.type = type;
		this.position = position;
	}

	@Override
	public String toString() {
		return type + "@[" + position.start + ".." + position.end + "]";
	}

	static TypeAtLoc from(Object astNode, PositionRecoveryStrategy recoveryStrategy) throws NoSuchMethodException, InvocationTargetException {
		return new TypeAtLoc(astNode.getClass().getSimpleName(), PositionRecovery.extractPosition(astNode, recoveryStrategy));
	}
}
