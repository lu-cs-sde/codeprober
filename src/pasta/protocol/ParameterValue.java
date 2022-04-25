package pasta.protocol;

import org.json.JSONObject;

import pasta.ast.AstNode;

public class ParameterValue extends ParameterType {

	private final Object value;

	public ParameterValue(Class<?> paramType, boolean isNodeType, Object value) {
		super(paramType, isNodeType);
		this.value = value;
	}
	
	public Object getUnpackedIfNode() {
		return isNodeType ? ((AstNode)value).underlyingAstNode : value;
	}

	public void serializeTo(JSONObject out) {
		super.serializeTo(out);
		out.put("value", value);
	}

}
