package codeprober.protocol.create;

import java.lang.reflect.Parameter;
import java.util.Collection;

import codeprober.AstInfo;
import codeprober.protocol.ParameterType;

public abstract class CreateType {

	public static ParameterType fromClass(Class<?> paramType, Class<?> baseAstClazz) {
		if (paramType == Boolean.TYPE || paramType == Integer.TYPE || paramType == String.class) {
			return new ParameterType(paramType, false);
		}
		if (baseAstClazz.isAssignableFrom(paramType)) {
			return new ParameterType(paramType, true);
		}
		// Two special cases that aren't visible to the end user, but important
		// for identifying NTA nodes.
		// "Object" is a fallback for when we don't know the type.
		// Again, this should never be shown to the user!
		if (paramType == Collection.class) {
			return new ParameterType(paramType, false);
		}
//		System.out.println("Unknown parameter type '" + paramType.getName() + "'");
		return null;
	}

	public static ParameterType[] fromParameters(AstInfo info, Parameter[] parameters) {
		final ParameterType[] ret = new ParameterType[parameters.length];
		for (int i = 0; i < ret.length; i++) {
			ret[i] = fromClass(parameters[i].getType(), info.baseAstClazz);
			if (ret[i] == null) {
				return null;
			}
		}
		return ret;
	}
}
