package codeprober.protocol.create;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import codeprober.AstInfo;
import codeprober.protocol.data.NullableNodeLocator;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.PropertyArgCollection;

public abstract class CreateType {

	public static PropertyArg fromClass(Class<?> param, Class<?> baseAstClazz, Object realValue) {
		if (param == Boolean.TYPE) {
			return PropertyArg.fromBool(false);
		}
		if (param == Integer.TYPE) {
			return PropertyArg.fromInteger(0);
		}
		if (param == String.class) {
			return PropertyArg.fromString("");
		}
		if (param == Collection.class || param == List.class || param == ArrayList.class || param == Set.class || param == HashSet.class) {
			return PropertyArg.fromCollection(new PropertyArgCollection(param.getName(), new ArrayList<>()));
		}
		if (param == OutputStream.class || param == PrintStream.class) {
			return PropertyArg.fromOutputstream(param.getName());
		}
		if (baseAstClazz.isAssignableFrom(param)) {
			return PropertyArg.fromNodeLocator(new NullableNodeLocator(param.getName(), null));
		}
		if (param.isInterface() && realValue != null && baseAstClazz.isInstance(realValue)) {
			// Formal parameter is an interface, actual value is an AST node.
			// We will treat the parameter as an AST type
			return PropertyArg.fromNodeLocator(new NullableNodeLocator(param.getName(), null));
		}
		if (param == Object.class) {
			return PropertyArg.fromAny(PropertyArg.fromString(""));
		}
		return null;
	}

	public static List<PropertyArg> fromParameters(AstInfo info, Parameter[] parameters) {
		final List<PropertyArg> ret = new ArrayList<>();
		for (Parameter p : parameters) {
			final PropertyArg arg = fromClass(p.getType(), info.baseAstClazz, null);
			if (arg == null) {
				return null;
			}
			ret.add(arg);
		}
		return ret;
	}
}
