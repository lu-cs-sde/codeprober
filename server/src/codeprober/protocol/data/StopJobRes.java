package codeprober.protocol.data;

import org.json.JSONObject;

public class StopJobRes implements codeprober.util.JsonUtil.ToJsonable {
  public final String err;
  public StopJobRes() {
    this(null);
  }
  public StopJobRes(String err) {
    this.err = err;
  }

  public static StopJobRes fromJSON(JSONObject obj) {
    return new StopJobRes(
      obj.has("err") ? (obj.getString("err")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (err != null) _ret.put("err", err);
    return _ret;
  }
}
