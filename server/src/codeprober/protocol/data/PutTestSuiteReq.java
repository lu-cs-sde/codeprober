package codeprober.protocol.data;

import org.json.JSONObject;

public class PutTestSuiteReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String suite;
  public final TestSuite contents;
  public PutTestSuiteReq(String suite, TestSuite contents) {
    this.type = "Test:PutTestSuite";
    this.suite = suite;
    this.contents = contents;
  }

  public static PutTestSuiteReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Test:PutTestSuite");
    return new PutTestSuiteReq(
      obj.getString("suite")
    , TestSuite.fromJSON(obj.getJSONObject("contents"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("suite", suite);
    _ret.put("contents", contents.toJSON());
    return _ret;
  }
}
