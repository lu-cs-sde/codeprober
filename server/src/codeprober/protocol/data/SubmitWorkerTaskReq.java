package codeprober.protocol.data;

import org.json.JSONObject;

public class SubmitWorkerTaskReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final long job;
  public final org.json.JSONObject data;
  public SubmitWorkerTaskReq(long job, org.json.JSONObject data) {
    this.type = "Concurrent:SubmitTask";
    this.job = job;
    this.data = data;
  }

  public static SubmitWorkerTaskReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Concurrent:SubmitTask");
    return new SubmitWorkerTaskReq(
      obj.getLong("job")
    , obj.getJSONObject("data")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("job", job);
    _ret.put("data", data);
    return _ret;
  }
}
