package codeprober.protocol.data;

import org.json.JSONObject;

public class HoverRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<String> lines;
  public final Integer originContextStart;
  public final Integer originContextEnd;
  public final Integer remoteContextStart;
  public final Integer remoteContextEnd;
  public HoverRes(java.util.List<String> lines, Integer originContextStart, Integer originContextEnd, Integer remoteContextStart) {
    this(lines, originContextStart, originContextEnd, remoteContextStart, (Integer)null);
  }
  public HoverRes(java.util.List<String> lines, Integer originContextStart, Integer originContextEnd) {
    this(lines, originContextStart, originContextEnd, (Integer)null, (Integer)null);
  }
  public HoverRes(java.util.List<String> lines, Integer originContextStart) {
    this(lines, originContextStart, (Integer)null, (Integer)null, (Integer)null);
  }
  public HoverRes(java.util.List<String> lines) {
    this(lines, (Integer)null, (Integer)null, (Integer)null, (Integer)null);
  }
  public HoverRes() {
    this((java.util.List<String>)null, (Integer)null, (Integer)null, (Integer)null, (Integer)null);
  }
  public HoverRes(java.util.List<String> lines, Integer originContextStart, Integer originContextEnd, Integer remoteContextStart, Integer remoteContextEnd) {
    this.lines = lines;
    this.originContextStart = originContextStart;
    this.originContextEnd = originContextEnd;
    this.remoteContextStart = remoteContextStart;
    this.remoteContextEnd = remoteContextEnd;
  }
  public HoverRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public HoverRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.lines = src.readBoolean() ? codeprober.util.JsonUtil.<String>readDataArr(src, () -> src.readUTF()) : null;
    this.originContextStart = src.readBoolean() ? src.readInt() : null;
    this.originContextEnd = src.readBoolean() ? src.readInt() : null;
    this.remoteContextStart = src.readBoolean() ? src.readInt() : null;
    this.remoteContextEnd = src.readBoolean() ? src.readInt() : null;
  }

  public static HoverRes fromJSON(JSONObject obj) {
    return new HoverRes(
      obj.has("lines") ? (codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("lines"), (arr1, idx1) -> arr1.getString(idx1))) : null
    , obj.has("originContextStart") ? (obj.getInt("originContextStart")) : null
    , obj.has("originContextEnd") ? (obj.getInt("originContextEnd")) : null
    , obj.has("remoteContextStart") ? (obj.getInt("remoteContextStart")) : null
    , obj.has("remoteContextEnd") ? (obj.getInt("remoteContextEnd")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (lines != null) _ret.put("lines", new org.json.JSONArray(lines));
    if (originContextStart != null) _ret.put("originContextStart", originContextStart);
    if (originContextEnd != null) _ret.put("originContextEnd", originContextEnd);
    if (remoteContextStart != null) _ret.put("remoteContextStart", remoteContextStart);
    if (remoteContextEnd != null) _ret.put("remoteContextEnd", remoteContextEnd);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    if (lines != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<String>writeDataArr(dst, lines, ent1 -> dst.writeUTF(ent1));; } else { dst.writeBoolean(false); }
    if (originContextStart != null) { dst.writeBoolean(true); dst.writeInt(originContextStart);; } else { dst.writeBoolean(false); }
    if (originContextEnd != null) { dst.writeBoolean(true); dst.writeInt(originContextEnd);; } else { dst.writeBoolean(false); }
    if (remoteContextStart != null) { dst.writeBoolean(true); dst.writeInt(remoteContextStart);; } else { dst.writeBoolean(false); }
    if (remoteContextEnd != null) { dst.writeBoolean(true); dst.writeInt(remoteContextEnd);; } else { dst.writeBoolean(false); }
  }
}
