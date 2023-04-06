package codeprober.protocol.data;

import org.json.JSONObject;

public class PollWorkerStatusReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final long job;
  public PollWorkerStatusReq(long job) {
    this.type = "Concurrent:PollWorkerStatus";
    this.job = job;
  }

  public static PollWorkerStatusReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Concurrent:PollWorkerStatus");
    return new PollWorkerStatusReq(
      obj.getLong("job")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("job", job);
    return _ret;
  }
}
