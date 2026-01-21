package codeprober.protocol.data;

import org.json.JSONObject;

public class NestedTest implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<Integer> path;
  public final Property property;
  public final java.util.List<RpcBodyLine> expectedOutput;
  public final java.util.List<NestedTest> nestedProperties;
  public NestedTest(java.util.List<Integer> path, Property property, java.util.List<RpcBodyLine> expectedOutput, java.util.List<NestedTest> nestedProperties) {
    this.path = path;
    this.property = property;
    this.expectedOutput = expectedOutput;
    this.nestedProperties = nestedProperties;
  }
  public NestedTest(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public NestedTest(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.path = codeprober.util.JsonUtil.<Integer>readDataArr(src, () -> src.readInt());
    this.property = new Property(src);
    this.expectedOutput = codeprober.util.JsonUtil.<RpcBodyLine>readDataArr(src, () -> new RpcBodyLine(src));
    this.nestedProperties = codeprober.util.JsonUtil.<NestedTest>readDataArr(src, () -> new NestedTest(src));
  }

  public static NestedTest fromJSON(JSONObject obj) {
    return new NestedTest(
      codeprober.util.JsonUtil.<Integer>mapArr(obj.getJSONArray("path"), (arr34, idx34) -> arr34.getInt(idx34))
    , Property.fromJSON(obj.getJSONObject("property"))
    , codeprober.util.JsonUtil.<RpcBodyLine>mapArr(obj.getJSONArray("expectedOutput"), (arr35, idx35) -> RpcBodyLine.fromJSON(arr35.getJSONObject(idx35)))
    , codeprober.util.JsonUtil.<NestedTest>mapArr(obj.getJSONArray("nestedProperties"), (arr36, idx36) -> NestedTest.fromJSON(arr36.getJSONObject(idx36)))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("path", new org.json.JSONArray(path));
    _ret.put("property", property.toJSON());
    _ret.put("expectedOutput", new org.json.JSONArray(expectedOutput.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    _ret.put("nestedProperties", new org.json.JSONArray(nestedProperties.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    codeprober.util.JsonUtil.<Integer>writeDataArr(dst, path, ent34 -> dst.writeInt(ent34));
    property.writeTo(dst);
    codeprober.util.JsonUtil.<RpcBodyLine>writeDataArr(dst, expectedOutput, ent35 -> ent35.writeTo(dst));
    codeprober.util.JsonUtil.<NestedTest>writeDataArr(dst, nestedProperties, ent36 -> ent36.writeTo(dst));
  }
}
