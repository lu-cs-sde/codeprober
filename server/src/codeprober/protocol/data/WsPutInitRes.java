package codeprober.protocol.data;

import org.json.JSONObject;

public class WsPutInitRes implements codeprober.util.JsonUtil.ToJsonable {
  public final InitInfo info;
  public WsPutInitRes(InitInfo info) {
    this.info = info;
  }
  public WsPutInitRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public WsPutInitRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.info = new InitInfo(src);
  }

  public static WsPutInitRes fromJSON(JSONObject obj) {
    return new WsPutInitRes(
      InitInfo.fromJSON(obj.getJSONObject("info"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("info", info.toJSON());
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    info.writeTo(dst);
  }
}
