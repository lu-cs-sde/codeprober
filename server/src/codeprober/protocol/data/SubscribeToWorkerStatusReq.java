package codeprober.protocol.data;

import org.json.JSONObject;

public class SubscribeToWorkerStatusReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final int job;
  public SubscribeToWorkerStatusReq(int job) {
    this.type = "Concurrent:SubscribeToWorkerStatus";
    this.job = job;
  }

  public static SubscribeToWorkerStatusReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Concurrent:SubscribeToWorkerStatus");
    return new SubscribeToWorkerStatusReq(
      obj.getInt("job")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("job", job);
    return _ret;
  }
}
