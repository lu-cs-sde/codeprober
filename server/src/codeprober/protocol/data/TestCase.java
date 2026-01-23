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
  public TestCase(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public TestCase(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.name = src.readUTF();
    this.src = new ParsingRequestData(src);
    this.property = new Property(src);
    this.locator = new NodeLocator(src);
    this.assertType = codeprober.protocol.TestCaseAssertType.values()[src.readInt()];
    this.expectedOutput = codeprober.util.JsonUtil.<RpcBodyLine>readDataArr(src, () -> new RpcBodyLine(src));
    this.nestedProperties = codeprober.util.JsonUtil.<NestedTest>readDataArr(src, () -> new NestedTest(src));
  }

  public static TestCase fromJSON(JSONObject obj) {
    return new TestCase(
      obj.getString("name")
    , ParsingRequestData.fromJSON(obj.getJSONObject("src"))
    , Property.fromJSON(obj.getJSONObject("property"))
    , NodeLocator.fromJSON(obj.getJSONObject("locator"))
    , codeprober.protocol.TestCaseAssertType.parseFromJson(obj.getString("assertType"))
    , codeprober.util.JsonUtil.<RpcBodyLine>mapArr(obj.getJSONArray("expectedOutput"), (arr1, idx1) -> RpcBodyLine.fromJSON(arr1.getJSONObject(idx1)))
    , codeprober.util.JsonUtil.<NestedTest>mapArr(obj.getJSONArray("nestedProperties"), (arr2, idx2) -> NestedTest.fromJSON(arr2.getJSONObject(idx2)))
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
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeUTF(name);
    src.writeTo(dst);
    property.writeTo(dst);
    locator.writeTo(dst);
    dst.writeInt(assertType.ordinal());
    codeprober.util.JsonUtil.<RpcBodyLine>writeDataArr(dst, expectedOutput, ent1 -> ent1.writeTo(dst));
    codeprober.util.JsonUtil.<NestedTest>writeDataArr(dst, nestedProperties, ent2 -> ent2.writeTo(dst));
  }
}
