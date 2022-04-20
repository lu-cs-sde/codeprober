package pasta.protocol;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.function.Function;

import org.json.JSONObject;

import pasta.PositionRecoveryStrategy;
import pasta.ResolveNodeLocator;
import pasta.ResolveNodeLocator.ResolvedNode;

public class SerializableParameter extends SerializableParameterType {

	public final Object value;

	public SerializableParameter(Class<?> paramType, boolean isNodeType, Object value) {
		super(paramType, isNodeType);
		this.value = value;
	}

	public void serializeTo(JSONObject out) {
		super.serializeTo(out);
		out.put("value", value);
	}

	public static SerializableParameter decode(Class<?> paramType, Object paramValue,
			PositionRecoveryStrategy recoveryStrategy, Class<?> baseAstClazz) {
		final SerializableParameterType type = SerializableParameterType.decode(paramType, baseAstClazz);
		if (type == null) {
			return null;
		}
		if (!type.isNodeType) {
			return new SerializableParameter(type.paramType, type.isNodeType, paramValue);
		}
		final JSONObject locator;
		try {
			locator = ResolveNodeLocator.buildLocator(paramValue, recoveryStrategy, baseAstClazz);
		} catch (NoSuchMethodException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		return new SerializableParameter(type.paramType, type.isNodeType, locator);
//		JSONObject 
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

	public static SerializableParameter decode(JSONObject serializedForm, Object ast,
			PositionRecoveryStrategy recoveryStrategy, Function<String, Class<?>> loadCls, Class<?> baseAstType) {
		final String argTypeStr = serializedForm.getString("type");
		switch (argTypeStr) {
		case "java.lang.String": {
			// 'optString' to allow nullable values
			return new SerializableParameter(String.class, false, serializedForm.optString("value"));
		}
		case "int": {
			return new SerializableParameter(Integer.TYPE, false, serializedForm.getInt("value"));
		}
		case "boolean": {
			return new SerializableParameter(Boolean.TYPE, false, serializedForm.getBoolean("value"));
		}
		
		default: {
			if (serializedForm.getBoolean("isNodeType")) {
				Class<?> type = loadCls.apply(argTypeStr);

				if (serializedForm.isNull("value")) {
					return new SerializableParameter(type, true, null);
				} else {
					final ResolvedNode locatedArg = ResolveNodeLocator.resolve(ast, recoveryStrategy, loadCls,
							serializedForm.getJSONObject("value"), baseAstType);
					if (locatedArg == null) {
						System.out.println("Couldn't find node arg in the document. Try remaking the probe");
						return null;
					}
					return new SerializableParameter(type, true, locatedArg.node);
				}
			} else {
				System.out.println("Unknown attribute type '" + argTypeStr + "'");
				return null;
			}
		}
		}
	}
}
