package pasta.protocol.create;

import org.json.JSONObject;

import pasta.AstInfo;
import pasta.ast.AstNode;
import pasta.locator.CreateLocator;
import pasta.protocol.ParameterType;
import pasta.protocol.ParameterValue;

public abstract class CreateValue {

	public static ParameterValue fromInstance(AstInfo info, Class<?> valueType, Object value) {
		final ParameterType type = CreateType.fromClass(valueType, info.basAstClazz);
		if (type == null) {
			return null;
		}
		if (!type.isNodeType) {
			return new ParameterValue(type.paramType, type.isNodeType, value);
		}
		final JSONObject locator = CreateLocator.fromNode(info, new AstNode(value));
		return new ParameterValue(type.paramType, type.isNodeType, locator);

	}

}
