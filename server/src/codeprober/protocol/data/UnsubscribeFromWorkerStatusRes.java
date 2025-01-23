package codeprober.protocol.data;

import org.json.JSONObject;

public class UnsubscribeFromWorkerStatusRes implements codeprober.util.JsonUtil.ToJsonable {
  public final boolean ok;
  public UnsubscribeFromWorkerStatusRes(boolean ok) {
    this.ok = ok;
  }
  public UnsubscribeFromWorkerStatusRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public UnsubscribeFromWorkerStatusRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.ok = src.readBoolean();
  }

  public static UnsubscribeFromWorkerStatusRes fromJSON(JSONObject obj) {
    return new UnsubscribeFromWorkerStatusRes(
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
