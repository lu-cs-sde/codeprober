package codeprober.protocol.data;

import org.json.JSONObject;

public class EvaluatePropertyRes implements codeprober.util.JsonUtil.ToJsonable {
  public final PropertyEvaluationResult response;
  public EvaluatePropertyRes(PropertyEvaluationResult response) {
    this.response = response;
  }

  public static EvaluatePropertyRes fromJSON(JSONObject obj) {
    return new EvaluatePropertyRes(
      PropertyEvaluationResult.fromJSON(obj.getJSONObject("response"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("response", response.toJSON());
    return _ret;
  }
}
