package codeprober.protocol.data;

import org.json.JSONObject;

public class HoverRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<String> lines;
  public HoverRes() {
    this((java.util.List<String>)null);
  }
  public HoverRes(java.util.List<String> lines) {
    this.lines = lines;
  }
  public HoverRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public HoverRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.lines = src.readBoolean() ? codeprober.util.JsonUtil.<String>readDataArr(src, () -> src.readUTF()) : null;
  }

  public static HoverRes fromJSON(JSONObject obj) {
    return new HoverRes(
      obj.has("lines") ? (codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("lines"), (arr, idx) -> arr.getString(idx))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (lines != null) _ret.put("lines", new org.json.JSONArray(lines));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    if (lines != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<String>writeDataArr(dst, lines, ent -> dst.writeUTF(ent));; } else { dst.writeBoolean(false); }
  }
}
