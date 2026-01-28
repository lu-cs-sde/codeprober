package codeprober.metaprogramming;

import java.lang.invoke.MethodType;
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
		return invokeN(astNode, findCompatibleMethod(astNode.getClass(), mth, argTypes), argValues);
	}

	public static Object invokeN(Object astNode, Method m, Object[] argValues) {
		try {
			m.setAccessible(true);

			// Handle varargs methods
			if (m.isVarArgs()) {
				final int paramCount = m.getParameterCount();
				final int nonVarArgsCount = paramCount - 1;

				// Create new array with proper structure for varargs
				Object[] adjustedArgs = new Object[paramCount];

				// Copy non-varargs parameters
				for (int i = 0; i < nonVarArgsCount; i++) {
					adjustedArgs[i] = argValues[i];
				}

				// Wrap varargs parameters in an array
				final int varArgsCount = argValues.length - nonVarArgsCount;
				final Class<?> varArgComponentType = m.getParameterTypes()[nonVarArgsCount].getComponentType();
				final Object varArgsArray = java.lang.reflect.Array.newInstance(varArgComponentType, varArgsCount);

				for (int i = 0; i < varArgsCount; i++) {
					java.lang.reflect.Array.set(varArgsArray, i, argValues[nonVarArgsCount + i]);
				}

				adjustedArgs[nonVarArgsCount] = varArgsArray;
				argValues = adjustedArgs;
			}

			final Object val = m.invoke(astNode, argValues);
			return m.getReturnType() == Void.TYPE ? VOID_RETURN_VALUE : val;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
			throw new InvokeProblem(e);
		}
	}

	public static Method findCompatibleMethod(Class<?> clazz, String methodName, Class<?>[] argTypes)
			throws InvokeProblem {
		try {
			return doFindCompatibleMethod(clazz, methodName, argTypes);
		} catch (IllegalArgumentException | NoSuchMethodException | SecurityException e) {
			throw new InvokeProblem(e);
		}
	}

	private static Class<?> boxPrimitive(Class<?> primitive) {
		return MethodType.methodType(primitive).wrap().returnType();
	}

	private static Method doFindCompatibleMethod(Class<?> clazz, String methodName, Class<?>[] argTypes)
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
				final boolean isVarArgs = method.isVarArgs();

				// Check length compatibility
				if (isVarArgs) {
					// For varargs, we need at least (paramTypes.length - 1) arguments
					if (argTypes.length < paramTypes.length - 1) {
						continue;
					}
				} else {
					if (paramTypes.length != argTypes.length) {
						continue;
					}
				}

				boolean compatible = true;

				// Check non-varargs parameters
				int nonVarArgsCount = isVarArgs ? paramTypes.length - 1 : paramTypes.length;
				for (int i = 0; i < nonVarArgsCount; i++) {
					if (!paramTypes[i].isAssignableFrom(argTypes[i])) {
						// Check if argTypes[i] is a subtype of paramTypes[i]
						if (argTypes[i].isPrimitive() && paramTypes[i].isAssignableFrom(boxPrimitive(argTypes[i]))) {
							// Allow, autoboxing will make this work even if `isAssignableFrom` says no.
						} else {
							compatible = false;
						}
						break;
					}
				}

				// Check varargs parameters if applicable
				if (compatible && isVarArgs) {
					Class<?> varArgType = paramTypes[paramTypes.length - 1].getComponentType();
					for (int i = nonVarArgsCount; i < argTypes.length; i++) {
						if (!varArgType.isAssignableFrom(argTypes[i])) {
							if (argTypes[i].isPrimitive() && varArgType.isAssignableFrom(boxPrimitive(argTypes[i]))) {
								// Allow, autoboxing will make this work
							} else {
								compatible = false;
								break;
							}
						}
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
		return invoke0(astNode, findMostAccessibleMethod(astNode, mth));
	}

	public static Object invoke0(Object astNode, Method mth) {
		try {
			mth.setAccessible(true);

			// Handle varargs methods with zero trailing arguments
			if (mth.isVarArgs() && mth.getParameterCount() == 1) {
				// Create an empty array of the appropriate type for the varargs parameter
				final Class<?> varArgComponentType = mth.getParameterTypes()[0].getComponentType();
				final Object emptyVarArgsArray = java.lang.reflect.Array.newInstance(varArgComponentType, 0);
				final Object val = mth.invoke(astNode, emptyVarArgsArray);
				return mth.getReturnType() == Void.TYPE ? VOID_RETURN_VALUE : val;
			}

			final Object val = mth.invoke(astNode);
			return mth.getReturnType() == Void.TYPE ? VOID_RETURN_VALUE : val;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
			throw new InvokeProblem(e);
		}
	}

	public static Method findMostAccessibleMethod(Object obj, String methodName) throws InvokeProblem {
		try {
			final Method m = doFindMostAccessibleMethod(obj, methodName);
			if (m == null) {
				throw new NoSuchMethodException();
			}
			return m;
		} catch (IllegalArgumentException | NoSuchMethodException | SecurityException e) {
			throw new InvokeProblem(e);
		}
	}

	private static Method doFindMostAccessibleMethod(Object obj, String methodName) {
		if (obj == null) {
			return null;
		}

		Class<?> clazz = obj.getClass();
		Method candidate = null;

		try {
			// First, try to get the method from the actual class
			candidate = clazz.getMethod(methodName);

			// If the declaring class is not public, look for a better option
			if (!Modifier.isPublic(candidate.getDeclaringClass().getModifiers())) {
				Method better = findInInterfaces(clazz, methodName);
				if (better != null) {
					candidate = better;
				} else {
					// Check superclasses for a public declaration
					better = findInSuperclasses(clazz.getSuperclass(), methodName);
					if (better != null && Modifier.isPublic(better.getDeclaringClass().getModifiers())) {
						candidate = better;
					}
				}
			}
		} catch (NoSuchMethodException e) {
			// Method not found via getMethod, try getDeclaredMethod and search hierarchy
			candidate = findMethodInHierarchy(clazz, methodName);

			// If still not found, check for varargs methods that could match with zero args
			if (candidate == null) {
				candidate = findVarArgsMethodWithZeroArgs(clazz, methodName);
			}
		}

		return candidate;
	}

	private static Method findVarArgsMethodWithZeroArgs(Class<?> clazz, String methodName) {
		// Look for a varargs method where all parameters are varargs (i.e., only one
		// parameter that is varargs)
		for (Method method : clazz.getMethods()) {
			if (method.getName().equals(methodName) && method.isVarArgs() && method.getParameterCount() == 1) {
				return method;
			}
		}
		return null;
	}

	private static Method findInInterfaces(Class<?> clazz, String methodName) {
		// Check all interfaces (including inherited ones)
		for (Class<?> iface : clazz.getInterfaces()) {
			try {
				Method method = iface.getMethod(methodName);
				if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
					return method;
				}
			} catch (NoSuchMethodException ignored) {
				// Continue searching
			}

			// Recursively check parent interfaces
			Method found = findInInterfaces(iface, methodName);
			if (found != null)
				return found;
		}

		// Also check superclass interfaces
		Class<?> superclass = clazz.getSuperclass();
		if (superclass != null) {
			return findInInterfaces(superclass, methodName);
		}

		return null;
	}

	private static Method findInSuperclasses(Class<?> clazz, String methodName) {
		while (clazz != null) {
			try {
				Method method = clazz.getMethod(methodName);
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

	private static Method findMethodInHierarchy(Class<?> clazz, String methodName) {
		// This handles cases where the method might not be public
		Method result = null;

		while (clazz != null) {
			try {
				Method m = clazz.getDeclaredMethod(methodName);
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
			Method ifaceMethod = findInInterfaces(clazz, methodName);
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
