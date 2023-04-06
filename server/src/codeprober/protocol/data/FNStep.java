package codeprober.protocol.data;

import org.json.JSONObject;

public class FNStep implements codeprober.util.JsonUtil.ToJsonable {
  public final Property property;
  public FNStep(Property property) {
    this.property = property;
  }

  public static FNStep fromJSON(JSONObject obj) {
    return new FNStep(
      Property.fromJSON(obj.getJSONObject("property"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("property", property.toJSON());
    return _ret;
  }
}
