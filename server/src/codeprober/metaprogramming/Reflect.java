package codeprober.metaprogramming;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
			final Method m = findCompatibleMethod(astNode.getClass(), mth, argTypes);
			m.setAccessible(true);
			final Object val = m.invoke(astNode, argValues);
			return m.getReturnType() == Void.TYPE ? VOID_RETURN_VALUE : val;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
				| SecurityException e) {
			throw new InvokeProblem(e);
		}
	}

	private static Method findCompatibleMethod(Class<?> clazz, String methodName, Class<?>[] argTypes)
			throws NoSuchMethodException {
		// First try exact match (fast path)
		try {
			return clazz.getMethod(methodName, argTypes);
		} catch (NoSuchMethodException e) {
			// Search all public methods for compatible signatures instead
			for (Method method : clazz.getMethods()) {
				if (!method.getName().equals(methodName)) {
					continue;
				}

				final Class<?>[] paramTypes = method.getParameterTypes();
				if (paramTypes.length != argTypes.length) {
					continue;
				}

				boolean compatible = true;
				for (int i = 0; i < paramTypes.length; i++) {
					if (!paramTypes[i].isAssignableFrom(argTypes[i])) {
						// Check if argTypes[i] is a subtype of paramTypes[i]
						if (paramTypes[i] == Object.class && argTypes[i].isPrimitive()) {
							// Allow, autoboxing will make this work even if `isAssignableFrom` says no.
						} else {
							compatible = false;
						}
						break;
					}
				}

				if (compatible) {
					return method;
				}
			}
			// No compatible method found, throw original exception
			throw e;
		}
	}

	public static Object invoke0(Object astNode, String mth) {
		try {
			final Method m = findMostAccessibleMethod(astNode, mth); // astNode.getClass().getMethod(mth);
			if (m == null) {
				throw new NoSuchMethodException(mth);
			}
			m.setAccessible(true);
			final Object val = m.invoke(astNode);
			return m.getReturnType() == Void.TYPE ? VOID_RETURN_VALUE : val;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
				| SecurityException e) {
			throw new InvokeProblem(e);
		}
	}

	public static Method findMostAccessibleMethod(Object obj, String methodName, Class<?>... paramTypes) {
		if (obj == null) {
			return null;
		}

		Class<?> clazz = obj.getClass();
		Method candidate = null;

		try {
			// First, try to get the method from the actual class
			candidate = clazz.getMethod(methodName, paramTypes);

			// If the declaring class is not public, look for a better option
			if (!Modifier.isPublic(candidate.getDeclaringClass().getModifiers())) {
				Method better = findInInterfaces(clazz, methodName, paramTypes);
				if (better != null) {
					candidate = better;
				} else {
					// Check superclasses for a public declaration
					better = findInSuperclasses(clazz.getSuperclass(), methodName, paramTypes);
					if (better != null && Modifier.isPublic(better.getDeclaringClass().getModifiers())) {
						candidate = better;
					}
				}
			}
		} catch (NoSuchMethodException e) {
			// Method not found via getMethod, try getDeclaredMethod and search hierarchy
			candidate = findMethodInHierarchy(clazz, methodName, paramTypes);
		}

		return candidate;
	}

	private static Method findInInterfaces(Class<?> clazz, String methodName, Class<?>... paramTypes) {
		// Check all interfaces (including inherited ones)
		for (Class<?> iface : clazz.getInterfaces()) {
			try {
				Method method = iface.getMethod(methodName, paramTypes);
				if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
					return method;
				}
			} catch (NoSuchMethodException ignored) {
				// Continue searching
			}

			// Recursively check parent interfaces
			Method found = findInInterfaces(iface, methodName, paramTypes);
			if (found != null)
				return found;
		}

		// Also check superclass interfaces
		Class<?> superclass = clazz.getSuperclass();
		if (superclass != null) {
			return findInInterfaces(superclass, methodName, paramTypes);
		}

		return null;
	}

	private static Method findInSuperclasses(Class<?> clazz, String methodName, Class<?>... paramTypes) {
		while (clazz != null) {
			try {
				Method method = clazz.getMethod(methodName, paramTypes);
				if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
					return method;
				}
			} catch (NoSuchMethodException ignored) {
				// Continue searching
			}
			clazz = clazz.getSuperclass();
		}
		return null;
	}

	private static Method findMethodInHierarchy(Class<?> clazz, String methodName, Class<?>... paramTypes) {
		// This handles cases where the method might not be public
		Method result = null;

		while (clazz != null) {
			try {
				Method m = clazz.getDeclaredMethod(methodName, paramTypes);
				if (result == null || Modifier.isPublic(m.getModifiers())) {
					result = m;
					if (Modifier.isPublic(m.getModifiers())
							&& Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
						// Found a public method in a public class, this is ideal
						return result;
					}
				}
			} catch (NoSuchMethodException ignored) {
			}

			// Check interfaces at this level
			Method ifaceMethod = findInInterfaces(clazz, methodName, paramTypes);
			if (ifaceMethod != null) {
				return ifaceMethod;
			}

			clazz = clazz.getSuperclass();
		}

		if (result != null) {
			result.setAccessible(true); // May still need this for non-public methods
		}
		return result;
	}
}
