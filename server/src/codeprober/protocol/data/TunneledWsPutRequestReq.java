package codeprober.protocol.data;

import org.json.JSONObject;

public class TunneledWsPutRequestReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String session;
  public final org.json.JSONObject request;
  public TunneledWsPutRequestReq(String session, org.json.JSONObject request) {
    this.type = "wsput:tunnel";
    this.session = session;
    this.request = request;
  }
  public TunneledWsPutRequestReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public TunneledWsPutRequestReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "wsput:tunnel";
    this.session = src.readUTF();
    this.request = new org.json.JSONObject(src.readUTF());
  }

  public static TunneledWsPutRequestReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "wsput:tunnel");
    return new TunneledWsPutRequestReq(
      obj.getString("session")
    , obj.getJSONObject("request")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("session", session);
    _ret.put("request", request);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(session);
    dst.writeUTF(request.toString());
  }
}
