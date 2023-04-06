package codeprober.protocol.data;

import org.json.JSONObject;

public class GetWorkerStatusRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<String> stackTrace;
  public GetWorkerStatusRes(java.util.List<String> stackTrace) {
    this.stackTrace = stackTrace;
  }

  public static GetWorkerStatusRes fromJSON(JSONObject obj) {
    return new GetWorkerStatusRes(
      codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("stackTrace"), (arr, idx) -> arr.getString(idx))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("stackTrace", new org.json.JSONArray(stackTrace));
    return _ret;
  }
}
