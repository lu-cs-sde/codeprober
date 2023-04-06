package codeprober.protocol.data;

import org.json.JSONObject;

public class ListTestSuitesRes implements codeprober.util.JsonUtil.ToJsonable {
  public final TestSuiteListOrError result;
  public ListTestSuitesRes(TestSuiteListOrError result) {
    this.result = result;
  }

  public static ListTestSuitesRes fromJSON(JSONObject obj) {
    return new ListTestSuitesRes(
      TestSuiteListOrError.fromJSON(obj.getJSONObject("result"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("result", result.toJSON());
    return _ret;
  }
}
