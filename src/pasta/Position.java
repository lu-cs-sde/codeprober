package pasta;

import java.lang.reflect.InvocationTargetException;

class Position {
	public final int start, end;

	public Position(int start, int end) {
		this.start = start;
		this.end = end;
	}

	public boolean isMeaningful() {
		return start != 0 || end != 0;
	}

	static Position from(Object astNode) throws NoSuchMethodException, InvocationTargetException {
		return new Position((Integer) Reflect.throwingInvoke0(astNode, "getStart"),
				(Integer) Reflect.throwingInvoke0(astNode, "getEnd"));
	}
}