package codeprober.protocol.data;

import org.json.JSONObject;

public class SubmitWorkerTaskRes implements codeprober.util.JsonUtil.ToJsonable {
  public final boolean ok;
  public SubmitWorkerTaskRes(boolean ok) {
    this.ok = ok;
  }

  public static SubmitWorkerTaskRes fromJSON(JSONObject obj) {
    return new SubmitWorkerTaskRes(
      obj.getBoolean("ok")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("ok", ok);
    return _ret;
  }
}
