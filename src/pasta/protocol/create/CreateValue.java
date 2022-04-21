package pasta.protocol.create;

import java.lang.reflect.InvocationTargetException;

import org.json.JSONObject;

import pasta.AstInfo;
import pasta.locator.CreateLocator;
import pasta.protocol.ParameterValue;
import pasta.protocol.ParameterType;

public abstract class CreateValue {

	public static ParameterValue fromInstance(AstInfo info, Class<?> valueType, Object value) {
		final ParameterType type = CreateType.fromClass(valueType, info.basAstClazz);
		if (type == null) {
			return null;
		}
		if (!type.isNodeType) {
			return new ParameterValue(type.paramType, type.isNodeType, value);
		}
		final JSONObject locator;
		try {
			locator = CreateLocator.fromNode(info, value);
		} catch (NoSuchMethodException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		return new ParameterValue(type.paramType, type.isNodeType, locator);

	}

}
