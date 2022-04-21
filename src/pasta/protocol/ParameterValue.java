package pasta.protocol;

import org.json.JSONObject;

public class ParameterValue extends ParameterType {

	public final Object value;

	public ParameterValue(Class<?> paramType, boolean isNodeType, Object value) {
		super(paramType, isNodeType);
		this.value = value;
	}

	public void serializeTo(JSONObject out) {
		super.serializeTo(out);
		out.put("value", value);
	}

}
