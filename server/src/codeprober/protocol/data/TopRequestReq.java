package codeprober.protocol.data;

import org.json.JSONObject;

public class TopRequestReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final long id;
  public final org.json.JSONObject data;
  public TopRequestReq(long id, org.json.JSONObject data) {
    this.type = "rpc";
    this.id = id;
    this.data = data;
  }

  public static TopRequestReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "rpc");
    return new TopRequestReq(
      obj.getLong("id")
    , obj.getJSONObject("data")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("id", id);
    _ret.put("data", data);
    return _ret;
  }
}
