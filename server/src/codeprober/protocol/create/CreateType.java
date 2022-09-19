package codeprober.protocol.create;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Parameter;
import java.util.Collection;

import codeprober.AstInfo;
import codeprober.protocol.ParameterType;
import codeprober.protocol.ParameterTypeDetail;

public abstract class CreateType {

	public static ParameterType fromClass(Class<?> paramType, Class<?> baseAstClazz) {
		if (paramType == Boolean.TYPE || paramType == Integer.TYPE || paramType == String.class) {
			return new ParameterType(paramType, ParameterTypeDetail.NORMAL);
		}
		if (baseAstClazz.isAssignableFrom(paramType)) {
			return new ParameterType(paramType, ParameterTypeDetail.AST_NODE);
		}
		// Two special cases that aren't visible to the end user, but important
		// for identifying NTA nodes.
		// "Object" is a fallback for when we don't know the type.
		// Again, this should never be shown to the user!
		if (paramType == Collection.class) {
			return new ParameterType(paramType, ParameterTypeDetail.NORMAL);
		}

		if (paramType == OutputStream.class || paramType == PrintStream.class) {
			return new ParameterType(paramType, ParameterTypeDetail.OUTPUTSTREAM);
		}
//		System.out.println("Unknown parameter type '" + paramType.getName() + " , " + paramType.getClass().getName()
//				+ ", " + paramType.getClassLoader() + "' , " + OutputStream.class.isAssignableFrom(paramType.getClass())
//				+ " ;; " + paramType.getClass().isAssignableFrom(OutputStream.class));
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
