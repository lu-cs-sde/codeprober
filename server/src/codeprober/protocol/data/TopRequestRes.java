package codeprober.protocol.data;

import org.json.JSONObject;

public class TopRequestRes implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final long id;
  public final TopRequestResponseData data;
  public TopRequestRes(long id, TopRequestResponseData data) {
    this.type = "rpc";
    this.id = id;
    this.data = data;
  }

  public static TopRequestRes fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "rpc");
    return new TopRequestRes(
      obj.getLong("id")
    , TopRequestResponseData.fromJSON(obj.getJSONObject("data"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("id", id);
    _ret.put("data", data.toJSON());
    return _ret;
  }
}
