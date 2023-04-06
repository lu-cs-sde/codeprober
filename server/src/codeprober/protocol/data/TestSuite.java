package codeprober.protocol.data;

import org.json.JSONObject;

public class TestSuite implements codeprober.util.JsonUtil.ToJsonable {
  public final int v;
  public final java.util.List<TestCase> cases;
  public TestSuite(int v, java.util.List<TestCase> cases) {
    this.v = v;
    this.cases = cases;
  }

  public static TestSuite fromJSON(JSONObject obj) {
    return new TestSuite(
      obj.getInt("v")
    , codeprober.util.JsonUtil.<TestCase>mapArr(obj.getJSONArray("cases"), (arr, idx) -> TestCase.fromJSON(arr.getJSONObject(idx)))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("v", v);
    _ret.put("cases", new org.json.JSONArray(cases.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
}
