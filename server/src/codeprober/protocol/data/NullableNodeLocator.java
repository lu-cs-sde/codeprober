package codeprober.protocol.data;

import org.json.JSONObject;

public class NullableNodeLocator implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final NodeLocator value;
  public NullableNodeLocator(String type) {
    this(type, null);
  }
  public NullableNodeLocator(String type, NodeLocator value) {
    this.type = type;
    this.value = value;
  }

  public static NullableNodeLocator fromJSON(JSONObject obj) {
    return new NullableNodeLocator(
      obj.getString("type")
    , obj.has("value") ? (NodeLocator.fromJSON(obj.getJSONObject("value"))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    if (value != null) _ret.put("value", value.toJSON());
    return _ret;
  }
}
