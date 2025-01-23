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
  public PutTestSuiteReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public PutTestSuiteReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "Test:PutTestSuite";
    this.suite = src.readUTF();
    this.contents = new TestSuite(src);
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
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(suite);
    contents.writeTo(dst);
  }
}
