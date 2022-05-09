package pasta.protocol;

import org.json.JSONObject;

import pasta.AstInfo;
import pasta.ast.AstNode;
import pasta.locator.CreateLocator;

public class ParameterValue extends ParameterType {

	private final AstInfo info;
	private final Object value;

	public ParameterValue(Class<?> paramType, boolean isNodeType, AstInfo info, Object value) {
		super(paramType, isNodeType);
		this.info = info;
		this.value = value;
	}
	
	public Object getUnpackedIfNode() {
		return (isNodeType && value != null) ? ((AstNode)value).underlyingAstNode : value;
	}

	public void serializeTo(JSONObject out) {
		super.serializeTo(out);
		
		if (value == null) {
			out.put("value", JSONObject.NULL);
		} else if (isNodeType) {
			out.put("value", CreateLocator.fromNode(info, (AstNode)value));
		} else {
			out.put("value", value);
		}
	}

}
