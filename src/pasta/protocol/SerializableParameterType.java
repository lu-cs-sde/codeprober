package pasta.protocol;

import java.lang.reflect.Parameter;

import org.json.JSONObject;

public class SerializableParameterType {

	public final Class<?> paramType;
	public final boolean isNodeType;

	public SerializableParameterType(Class<?> paramType, boolean isNodeType) {
		this.paramType = paramType;
		this.isNodeType = isNodeType;
	}

	public void serializeTo(JSONObject out) {
		out.put("type", paramType.getName());
		out.put("isNodeType", isNodeType);
	}

	public static SerializableParameterType decode(Class<?> paramClazz, Class<?> baseAstClazz) {
		if (paramClazz == Boolean.TYPE || paramClazz == Integer.TYPE || paramClazz == String.class) {
			return new SerializableParameterType(paramClazz, false);
		}
		if (baseAstClazz.isAssignableFrom(paramClazz)) {
			return new SerializableParameterType(paramClazz, true);
		}
		System.out.println("Unknown parameter type '" + paramClazz.getName() + "'");
		return null;
	}

	public static SerializableParameterType[] decodeParameters(Parameter[] params, Class<?> baseAstClazz) {
		final SerializableParameterType[] ret = new SerializableParameterType[params.length];
		for (int i = 0; i < ret.length; i++) {
			ret[i] = decode(params[i].getType(), baseAstClazz);
			if (ret[i] == null) {
				return null;
			}
		}
		return ret;
	}

}
