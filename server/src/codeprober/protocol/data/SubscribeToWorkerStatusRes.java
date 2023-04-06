package codeprober.protocol.data;

import org.json.JSONObject;

public class SubscribeToWorkerStatusRes implements codeprober.util.JsonUtil.ToJsonable {
  public final int subscriberId;
  public SubscribeToWorkerStatusRes(int subscriberId) {
    this.subscriberId = subscriberId;
  }

  public static SubscribeToWorkerStatusRes fromJSON(JSONObject obj) {
    return new SubscribeToWorkerStatusRes(
      obj.getInt("subscriberId")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("subscriberId", subscriberId);
    return _ret;
  }
}
