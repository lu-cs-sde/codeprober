package codeprober.protocol.data;

import org.json.JSONObject;

public class UnlinkWorkspacePathRes implements codeprober.util.JsonUtil.ToJsonable {
  public final boolean ok;
  public UnlinkWorkspacePathRes(boolean ok) {
    this.ok = ok;
  }
  public UnlinkWorkspacePathRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public UnlinkWorkspacePathRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.ok = src.readBoolean();
  }

  public static UnlinkWorkspacePathRes fromJSON(JSONObject obj) {
    return new UnlinkWorkspacePathRes(
      obj.getBoolean("ok")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("ok", ok);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeBoolean(ok);
  }
}
