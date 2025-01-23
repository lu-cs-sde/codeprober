package codeprober.protocol.data;

import org.json.JSONObject;

public class WsPutLongpollReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String session;
  public final int etag;
  public WsPutLongpollReq(String session, int etag) {
    this.type = "wsput:longpoll";
    this.session = session;
    this.etag = etag;
  }
  public WsPutLongpollReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public WsPutLongpollReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "wsput:longpoll";
    this.session = src.readUTF();
    this.etag = src.readInt();
  }

  public static WsPutLongpollReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "wsput:longpoll");
    return new WsPutLongpollReq(
      obj.getString("session")
    , obj.getInt("etag")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("session", session);
    _ret.put("etag", etag);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(session);
    dst.writeInt(etag);
  }
}
