package pasta.protocol;

import org.json.JSONObject;

public class ParameterType {

	public final Class<?> paramType;
	public final boolean isNodeType;

	public ParameterType(Class<?> paramType, boolean isNodeType) {
		this.paramType = paramType;
		this.isNodeType = isNodeType;
	}

	public void serializeTo(JSONObject out) {
		out.put("type", paramType.getName());
		out.put("isNodeType", isNodeType);
	}

	public JSONObject toJson() {
		JSONObject obj = new JSONObject();
		serializeTo(obj);
		return obj;
	}
}
