package codeprober.protocol.data;

import org.json.JSONObject;

public class TestCase implements codeprober.util.JsonUtil.ToJsonable {
  public final String name;
  public final ParsingRequestData src;
  public final Property property;
  public final NodeLocator locator;
  public final codeprober.protocol.TestCaseAssertType assertType;
  public final java.util.List<RpcBodyLine> expectedOutput;
  public final java.util.List<NestedTest> nestedProperties;
  public TestCase(String name, ParsingRequestData src, Property property, NodeLocator locator, codeprober.protocol.TestCaseAssertType assertType, java.util.List<RpcBodyLine> expectedOutput, java.util.List<NestedTest> nestedProperties) {
    this.name = name;
    this.src = src;
    this.property = property;
    this.locator = locator;
    this.assertType = assertType;
    this.expectedOutput = expectedOutput;
    this.nestedProperties = nestedProperties;
  }

  public static TestCase fromJSON(JSONObject obj) {
    return new TestCase(
      obj.getString("name")
    , ParsingRequestData.fromJSON(obj.getJSONObject("src"))
    , Property.fromJSON(obj.getJSONObject("property"))
    , NodeLocator.fromJSON(obj.getJSONObject("locator"))
    , codeprober.protocol.TestCaseAssertType.parseFromJson(obj.getString("assertType"))
    , codeprober.util.JsonUtil.<RpcBodyLine>mapArr(obj.getJSONArray("expectedOutput"), (arr, idx) -> RpcBodyLine.fromJSON(arr.getJSONObject(idx)))
    , codeprober.util.JsonUtil.<NestedTest>mapArr(obj.getJSONArray("nestedProperties"), (arr, idx) -> NestedTest.fromJSON(arr.getJSONObject(idx)))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("name", name);
    _ret.put("src", src.toJSON());
    _ret.put("property", property.toJSON());
    _ret.put("locator", locator.toJSON());
    _ret.put("assertType", assertType.name());
    _ret.put("expectedOutput", new org.json.JSONArray(expectedOutput.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    _ret.put("nestedProperties", new org.json.JSONArray(nestedProperties.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
}
