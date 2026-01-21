package codeprober.protocol.data;

import org.json.JSONObject;

public class TestSuite implements codeprober.util.JsonUtil.ToJsonable {
  public final int v;
  public final java.util.List<TestCase> cases;
  public TestSuite(int v, java.util.List<TestCase> cases) {
    this.v = v;
    this.cases = cases;
  }
  public TestSuite(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public TestSuite(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.v = src.readInt();
    this.cases = codeprober.util.JsonUtil.<TestCase>readDataArr(src, () -> new TestCase(src));
  }

  public static TestSuite fromJSON(JSONObject obj) {
    return new TestSuite(
      obj.getInt("v")
    , codeprober.util.JsonUtil.<TestCase>mapArr(obj.getJSONArray("cases"), (arr16, idx16) -> TestCase.fromJSON(arr16.getJSONObject(idx16)))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("v", v);
    _ret.put("cases", new org.json.JSONArray(cases.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeInt(v);
    codeprober.util.JsonUtil.<TestCase>writeDataArr(dst, cases, ent16 -> ent16.writeTo(dst));
  }
}
