package codeprober.protocol.data;

import org.json.JSONObject;

public class PollWorkerStatusRes implements codeprober.util.JsonUtil.ToJsonable {
  public final boolean ok;
  public PollWorkerStatusRes(boolean ok) {
    this.ok = ok;
  }

  public static PollWorkerStatusRes fromJSON(JSONObject obj) {
    return new PollWorkerStatusRes(
      obj.getBoolean("ok")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("ok", ok);
    return _ret;
  }
}
