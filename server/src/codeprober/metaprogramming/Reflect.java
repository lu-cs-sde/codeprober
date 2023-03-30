package codeprober.metaprogramming;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class Reflect {

	public static final Object VOID_RETURN_VALUE = new Object() {
		@Override
		public String toString() {
			return "<void>";
		}
	};

	public static Object getParent(Object astNode) {
		return Reflect.invoke0(astNode, "getParent");
	}

	public static Object invokeN(Object astNode, String mth, Class<?>[] argTypes, Object[] argValues) {
		try {
			final Method m = astNode.getClass().getMethod(mth, argTypes);
			m.setAccessible(true);
			final Object val = m.invoke(astNode, argValues);
			return m.getReturnType() == Void.TYPE ? VOID_RETURN_VALUE : val;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
				| SecurityException e) {
			e.printStackTrace();
			for (Method m : astNode.getClass().getMethods()) {
				if (m.getName().contains("bestMatchingNode")) {
					System.out.println("other m : " + m);
					System.out.println("param types: " + Arrays.toString(m.getParameterTypes()));
				}
			}
			throw new InvokeProblem(e);
		}
	}

	public static Object invoke0(Object astNode, String mth) {
		try {
			final Method m = astNode.getClass().getMethod(mth);
			m.setAccessible(true);
			final Object val = m.invoke(astNode);
			return m.getReturnType() == Void.TYPE ? VOID_RETURN_VALUE : val;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
				| SecurityException e) {
//			e.printStackTrace();
			throw new InvokeProblem(e);
		}
	}

//	public static Object throwingInvoke0(Object astNode, String mth) throws InvokeProblem {
//		try {
//			return astNode.getClass().getMethod(mth).invoke(astNode);
//		} catch (IllegalAccessException | IllegalArgumentException | SecurityException | InvocationTargetException
//				| NoSuchMethodException e) {
//			e.printStackTrace();
//			throw new InvokeProblem(e);
//		}
//	}

}
