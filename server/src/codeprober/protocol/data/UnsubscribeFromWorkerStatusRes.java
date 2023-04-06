package codeprober.protocol.data;

import org.json.JSONObject;

public class UnsubscribeFromWorkerStatusRes implements codeprober.util.JsonUtil.ToJsonable {
  public final boolean ok;
  public UnsubscribeFromWorkerStatusRes(boolean ok) {
    this.ok = ok;
  }

  public static UnsubscribeFromWorkerStatusRes fromJSON(JSONObject obj) {
    return new UnsubscribeFromWorkerStatusRes(
      obj.getBoolean("ok")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("ok", ok);
    return _ret;
  }
}
