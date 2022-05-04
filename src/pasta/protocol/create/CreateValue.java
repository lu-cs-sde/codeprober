package pasta.protocol.create;

import pasta.AstInfo;
import pasta.ast.AstNode;
import pasta.protocol.ParameterType;
import pasta.protocol.ParameterValue;

public abstract class CreateValue {

	public static ParameterValue fromInstance(AstInfo info, Class<?> valueType, Object value) {
		final ParameterType type = CreateType.fromClass(valueType, info.basAstClazz);
		if (type == null) {
			return null;
		}
		if (!type.isNodeType) {
			return new ParameterValue(type.paramType, type.isNodeType, info, value);
		}
		
		return new ParameterValue(type.paramType, type.isNodeType, info, new AstNode(value));
	}
}
