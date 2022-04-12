package pasta;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class Reflect {

	void f() {
	}

	static Object getParent(Object astNode) throws NoSuchMethodException, InvocationTargetException {
		return Reflect.throwingInvoke0(astNode, "getParent");
	}

	static Object getFirstChild(Object astNode) {
		return getNthChild(astNode, 0);
	}
	static Object getNthChild(Object astNode, int n) {
		int numCh = (Integer) invoke0(astNode, "getNumChild");
		if (n >= numCh) {
			return null;
		}
		return invokeN(astNode, "getChild", new Class<?>[] { Integer.TYPE }, new Object[] { n });
	}

	static Object invokeN(Object astNode, String mth, Class<?>[] argTypes, Object[] argValues) {
		try {
			return astNode.getClass().getMethod(mth, argTypes).invoke(astNode, argValues);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
				| SecurityException e) {
			e.printStackTrace();
			for (Method m : astNode.getClass().getMethods()) {
				if (m.getName().contains("bestMatchingNode")) {
					System.out.println("other m : " + m);
					System.out.println("param types: " + Arrays.toString(m.getParameterTypes()));
				}
			}
			throw new RuntimeException(e);
		}
	}

	private static Object throwingInvokeN(Object astNode, String mth, Class<?>[] argTypes, Object[] argValues)
			throws InvocationTargetException, NoSuchMethodException {
		try {
			return astNode.getClass().getMethod(mth, argTypes).invoke(astNode, argValues);
		} catch (IllegalAccessException | IllegalArgumentException | SecurityException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	static Object invoke0(Object astNode, String mth) {
		try {
			return astNode.getClass().getMethod(mth).invoke(astNode);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
				| SecurityException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	static Object throwingInvoke0(Object astNode, String mth)
			throws NoSuchMethodException, InvocationTargetException {
		try {
			return astNode.getClass().getMethod(mth).invoke(astNode);
		} catch (IllegalAccessException | IllegalArgumentException | SecurityException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
}
