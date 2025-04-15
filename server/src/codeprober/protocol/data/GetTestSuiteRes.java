package codeprober.protocol.data;

import org.json.JSONObject;

public class GetTestSuiteRes implements codeprober.util.JsonUtil.ToJsonable {
  public final TestSuiteOrError result;
  public GetTestSuiteRes(TestSuiteOrError result) {
    this.result = result;
  }
  public GetTestSuiteRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public GetTestSuiteRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.result = new TestSuiteOrError(src);
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
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    result.writeTo(dst);
  }
}
