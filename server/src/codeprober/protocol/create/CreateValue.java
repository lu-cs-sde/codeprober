package codeprober.protocol.create;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.protocol.ParameterType;
import codeprober.protocol.ParameterTypeDetail;
import codeprober.protocol.ParameterValue;

public abstract class CreateValue {

	private static Class<?> extractBaseClass(Type srcType) {
		if (srcType instanceof Class<?>) {
			return (Class<?>) srcType;
		}
		if (srcType instanceof ParameterizedType) {
			return extractBaseClass(((ParameterizedType) srcType).getRawType());
		}
		System.out.println("Unknown Type type '" + srcType.getClass() + "' | " + srcType);
		return null;
	}

	public static ParameterValue fromInstance(AstInfo info, Class<?> valueClazz, Type valueType, Object value) {
		final ParameterType type = CreateType.fromClass(valueClazz, info.baseAstClazz);
		if (type == null) {
			return null;
		}
		if (type.paramType == Collection.class) {
			if (!(valueType instanceof ParameterizedType)) {
				System.err.println("Expected Collection argument to be parameterized, got " + valueType);
				return null;
			}
			final ParameterizedType pt = (ParameterizedType) valueType;
			if (pt.getActualTypeArguments().length != 1) {
				System.err.println("Expected Collection argument to have one type parameter, got " + valueType + " w/ "
						+ pt.getActualTypeArguments().length + " arg(s)");
				return null;
			}
			final Type childType = pt.getActualTypeArguments()[0];

			final ArrayList<ParameterValue> remapped = new ArrayList<>();
			for (Object child : ((Collection<?>) value)) {
				// No no no
				// Just pass the Type from outside instead
				// Here, "if type instanceof ParType, get first thing, etc"
				// Not guaranteed to work, but "good enough" I think
				// Or be OK with Object.class??
				final Class<?> childClazz = extractBaseClass(childType);
				if (childClazz == null) {
					return null;
				}
				final ParameterValue childVal = CreateValue.fromInstance(info, childClazz, childType, child);
				if (childVal == null) {
					return null;
				}
				remapped.add(childVal);
			}
			return new ParameterValue(type.paramType, type.detail, info, remapped);
		}
		if (type.detail == ParameterTypeDetail.NORMAL) {
			return new ParameterValue(type.paramType, type.detail, info, value);
		}

		return new ParameterValue(type.paramType, type.detail, info, new AstNode(value));
	}
}
