package codeprober.protocol.data;

import org.json.JSONObject;

public class Refresh implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public Refresh() {
    this.type = "refresh";
  }

  public static Refresh fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "refresh");
    return new Refresh(
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    return _ret;
  }
}
