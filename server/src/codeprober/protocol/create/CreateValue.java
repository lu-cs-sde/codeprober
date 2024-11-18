package codeprober.protocol.create;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.CreateLocator;
import codeprober.protocol.data.NullableNodeLocator;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.PropertyArgCollection;

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

	public static PropertyArg fromInstance(AstInfo info, Class<?> valueClazz, Type valueType, Object value) {
		final PropertyArg arg = CreateType.fromClass(valueClazz, info.baseAstClazz, value);
		if (arg == null) {
			return null;
		}
		switch (arg.type) {
		case collection: {
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
			final ArrayList<PropertyArg> remapped = new ArrayList<>();
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
				final PropertyArg childVal = CreateValue.fromInstance(info, childClazz, childType, child);
				if (childVal == null) {
					return null;
				}
				remapped.add(childVal);
			}
			return PropertyArg.fromCollection(new PropertyArgCollection(arg.asCollection().type, remapped));
		}
		case nodeLocator:
			return PropertyArg.fromNodeLocator(new NullableNodeLocator(valueClazz.getName(), CreateLocator.fromNode(info, new AstNode(value))));
		case bool:
			return PropertyArg.fromBool((boolean) value);
		case integer:
			return PropertyArg.fromInteger((int) value);
		case outputstream:
			return PropertyArg.fromOutputstream(value.getClass().getName());
		case string:
			return PropertyArg.fromString((String) value);
		default:
			throw new RuntimeException("Unknown arg type " + arg.type);
		}
	}
}
