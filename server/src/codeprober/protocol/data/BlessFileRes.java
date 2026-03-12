package codeprober.protocol.data;

import org.json.JSONObject;

public class BlessFileRes implements codeprober.util.JsonUtil.ToJsonable {
  public final Integer numUpdatedProbes;
  public final String result;
  public BlessFileRes(Integer numUpdatedProbes) {
    this(numUpdatedProbes, (String)null);
  }
  public BlessFileRes() {
    this((Integer)null, (String)null);
  }
  public BlessFileRes(Integer numUpdatedProbes, String result) {
    this.numUpdatedProbes = numUpdatedProbes;
    this.result = result;
  }
  public BlessFileRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public BlessFileRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.numUpdatedProbes = src.readBoolean() ? src.readInt() : null;
    this.result = src.readBoolean() ? src.readUTF() : null;
  }

  public static BlessFileRes fromJSON(JSONObject obj) {
    return new BlessFileRes(
      obj.has("numUpdatedProbes") ? (obj.getInt("numUpdatedProbes")) : null
    , obj.has("result") ? (obj.getString("result")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (numUpdatedProbes != null) _ret.put("numUpdatedProbes", numUpdatedProbes);
    if (result != null) _ret.put("result", result);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    if (numUpdatedProbes != null) { dst.writeBoolean(true); dst.writeInt(numUpdatedProbes);; } else { dst.writeBoolean(false); }
    if (result != null) { dst.writeBoolean(true); dst.writeUTF(result);; } else { dst.writeBoolean(false); }
  }
}
