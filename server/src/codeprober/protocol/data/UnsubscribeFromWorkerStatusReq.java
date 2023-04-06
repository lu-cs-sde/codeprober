package codeprober.protocol.data;

import org.json.JSONObject;

public class UnsubscribeFromWorkerStatusReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final int job;
  public final int subscriberId;
  public UnsubscribeFromWorkerStatusReq(int job, int subscriberId) {
    this.type = "Concurrent:UnsubscribeFromWorkerStatus";
    this.job = job;
    this.subscriberId = subscriberId;
  }

  public static UnsubscribeFromWorkerStatusReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Concurrent:UnsubscribeFromWorkerStatus");
    return new UnsubscribeFromWorkerStatusReq(
      obj.getInt("job")
    , obj.getInt("subscriberId")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("job", job);
    _ret.put("subscriberId", subscriberId);
    return _ret;
  }
}
