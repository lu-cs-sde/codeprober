package codeprober.protocol.data;

import org.json.JSONObject;

public class ListTestSuitesReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public ListTestSuitesReq() {
    this.type = "Test:ListTestSuites";
  }
  public ListTestSuitesReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public ListTestSuitesReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "Test:ListTestSuites";
  }

  public static ListTestSuitesReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Test:ListTestSuites");
    return new ListTestSuitesReq(
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
  }
}
