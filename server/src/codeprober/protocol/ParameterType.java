package codeprober.protocol;

import org.json.JSONObject;

public class ParameterType {

	public final Class<?> paramType;
	public final ParameterTypeDetail detail;

	public ParameterType(Class<?> paramType, ParameterTypeDetail detail) {
		this.paramType = paramType;
		this.detail = detail;
	}

	public void serializeTo(JSONObject out) {
		out.put("type", paramType.getName());
		out.put("detail", detail.toString());
	}

	public JSONObject toJson() {
		JSONObject obj = new JSONObject();
		serializeTo(obj);
		return obj;
	}
}
