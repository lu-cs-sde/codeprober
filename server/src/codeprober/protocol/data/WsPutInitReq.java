package codeprober.protocol.data;

import org.json.JSONObject;

public class WsPutInitReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String session;
  public WsPutInitReq(String session) {
    this.type = "wsput:init";
    this.session = session;
  }
  public WsPutInitReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public WsPutInitReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "wsput:init";
    this.session = src.readUTF();
  }

  public static WsPutInitReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "wsput:init");
    return new WsPutInitReq(
      obj.getString("session")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("session", session);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(session);
  }
}
