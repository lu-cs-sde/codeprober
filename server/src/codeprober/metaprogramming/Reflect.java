package codeprober.metaprogramming;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class Reflect {

	public static Object getParent(Object astNode) {
		return Reflect.invoke0(astNode, "getParent");
	}

	public static Object invokeN(Object astNode, String mth, Class<?>[] argTypes, Object[] argValues) {
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
			throw new InvokeProblem(e);
		}
	}

	public static Object invoke0(Object astNode, String mth) {
		try {
			return astNode.getClass().getMethod(mth).invoke(astNode);
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
