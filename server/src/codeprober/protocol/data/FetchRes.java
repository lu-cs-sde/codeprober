package codeprober.protocol.data;

import org.json.JSONObject;

public class FetchRes implements codeprober.util.JsonUtil.ToJsonable {
  public final String result;
  public FetchRes() {
    this(null);
  }
  public FetchRes(String result) {
    this.result = result;
  }

  public static FetchRes fromJSON(JSONObject obj) {
    return new FetchRes(
      obj.has("result") ? (obj.getString("result")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (result != null) _ret.put("result", result);
    return _ret;
  }
}
