package codeprober.protocol.data;

import org.json.JSONObject;

public class GetTestSuiteReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String suite;
  public GetTestSuiteReq(String suite) {
    this.type = "Test:GetTestSuite";
    this.suite = suite;
  }
  public GetTestSuiteReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public GetTestSuiteReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "Test:GetTestSuite";
    this.suite = src.readUTF();
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
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(suite);
  }
}
