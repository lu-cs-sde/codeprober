package codeprober.protocol.create;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import codeprober.AstInfo;
import codeprober.protocol.data.NullableNodeLocator;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.PropertyArgCollection;

public abstract class CreateType {

	public static PropertyArg fromClass(Class<?> param, Class<?> baseAstClazz) {
		if (param == Boolean.TYPE) {
			return PropertyArg.fromBool(false);
		}
		if (param == Integer.TYPE) {
			return PropertyArg.fromInteger(0);
		}
		if (param == String.class) {
			return PropertyArg.fromString("");
		}
		if (param == Collection.class) {
			return PropertyArg.fromCollection(new PropertyArgCollection(param.getName(), new ArrayList<>()));
		}
		if (param == OutputStream.class || param == PrintStream.class) {
			return PropertyArg.fromOutputstream(param.getName());
		}
		if (baseAstClazz.isAssignableFrom(param)) {
			return PropertyArg.fromNodeLocator(new NullableNodeLocator(param.getName(), null));
		}
		return null;
	}

	public static List<PropertyArg> fromParameters(AstInfo info, Parameter[] parameters) {
		final List<PropertyArg> ret = new ArrayList<>();
		for (Parameter p : parameters) {
			final PropertyArg arg = fromClass(p.getType(), info.baseAstClazz);
			if (arg == null) {
				return null;
			}
			ret.add(arg);
		}
		return ret;
	}
}
