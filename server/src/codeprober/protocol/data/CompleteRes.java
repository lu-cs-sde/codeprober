package codeprober.protocol.data;

import org.json.JSONObject;

public class CompleteRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<String> lines;
  public CompleteRes() {
    this((java.util.List<String>)null);
  }
  public CompleteRes(java.util.List<String> lines) {
    this.lines = lines;
  }
  public CompleteRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public CompleteRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.lines = src.readBoolean() ? codeprober.util.JsonUtil.<String>readDataArr(src, () -> src.readUTF()) : null;
  }

  public static CompleteRes fromJSON(JSONObject obj) {
    return new CompleteRes(
      obj.has("lines") ? (codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("lines"), (arr, idx) -> arr.getString(idx))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("lines", new org.json.JSONArray(lines));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    if (lines != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<String>writeDataArr(dst, lines, ent -> dst.writeUTF(ent));; } else { dst.writeBoolean(false); }
  }
}
