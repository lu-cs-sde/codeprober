package codeprober.protocol.data;

import org.json.JSONObject;

public class GetWorkerStatusReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public GetWorkerStatusReq() {
    this.type = "Concurrent:GetWorkerStatus";
  }

  public static GetWorkerStatusReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Concurrent:GetWorkerStatus");
    return new GetWorkerStatusReq(
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    return _ret;
  }
}
