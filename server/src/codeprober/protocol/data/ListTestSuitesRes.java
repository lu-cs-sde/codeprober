package codeprober.protocol.data;

import org.json.JSONObject;

public class ListTestSuitesRes implements codeprober.util.JsonUtil.ToJsonable {
  public final TestSuiteListOrError result;
  public ListTestSuitesRes(TestSuiteListOrError result) {
    this.result = result;
  }
  public ListTestSuitesRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public ListTestSuitesRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.result = new TestSuiteListOrError(src);
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
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    result.writeTo(dst);
  }
}
