package codeprober.protocol.data;

import org.json.JSONObject;

public class Refresh implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public Refresh() {
    this.type = "refresh";
  }
  public Refresh(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public Refresh(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "refresh";
  }

  public static Refresh fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "refresh");
    return new Refresh(
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
