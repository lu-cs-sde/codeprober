package codeprober.protocol.data;

import org.json.JSONObject;

public class GetTestSuiteReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String suite;
  public GetTestSuiteReq(String suite) {
    this.type = "Test:GetTestSuite";
    this.suite = suite;
  }

  public static GetTestSuiteReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Test:GetTestSuite");
    return new GetTestSuiteReq(
      obj.getString("suite")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("suite", suite);
    return _ret;
  }
}
