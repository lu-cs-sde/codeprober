package codeprober.protocol.data;

import org.json.JSONObject;

public class FetchReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String url;
  public FetchReq(String url) {
    this.type = "Fetch";
    this.url = url;
  }

  public static FetchReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Fetch");
    return new FetchReq(
      obj.getString("url")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("url", url);
    return _ret;
  }
}
