package codeprober.protocol.data;

import org.json.JSONObject;

public class StopJobRes implements codeprober.util.JsonUtil.ToJsonable {
  public final String err;
  public StopJobRes() {
    this((String)null);
  }
  public StopJobRes(String err) {
    this.err = err;
  }
  public StopJobRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public StopJobRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.err = src.readBoolean() ? src.readUTF() : null;
  }

  public static StopJobRes fromJSON(JSONObject obj) {
    return new StopJobRes(
      obj.has("err") ? (obj.getString("err")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (err != null) _ret.put("err", err);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    if (err != null) { dst.writeBoolean(true); dst.writeUTF(err);; } else { dst.writeBoolean(false); }
  }
}
