package codeprober.protocol.data;

import org.json.JSONObject;

public class GetTestSuiteRes implements codeprober.util.JsonUtil.ToJsonable {
  public final TestSuiteOrError result;
  public GetTestSuiteRes(TestSuiteOrError result) {
    this.result = result;
  }

  public static GetTestSuiteRes fromJSON(JSONObject obj) {
    return new GetTestSuiteRes(
      TestSuiteOrError.fromJSON(obj.getJSONObject("result"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("result", result.toJSON());
    return _ret;
  }
}
