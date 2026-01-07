package codeprober.protocol.data;

import org.json.JSONObject;

public class CompleteRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<CompletionItem> lines;
  public final Integer originContextStart;
  public final Integer originContextEnd;
  public CompleteRes(java.util.List<CompletionItem> lines, Integer originContextStart) {
    this(lines, originContextStart, (Integer)null);
  }
  public CompleteRes(java.util.List<CompletionItem> lines) {
    this(lines, (Integer)null, (Integer)null);
  }
  public CompleteRes() {
    this((java.util.List<CompletionItem>)null, (Integer)null, (Integer)null);
  }
  public CompleteRes(java.util.List<CompletionItem> lines, Integer originContextStart, Integer originContextEnd) {
    this.lines = lines;
    this.originContextStart = originContextStart;
    this.originContextEnd = originContextEnd;
  }
  public CompleteRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public CompleteRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.lines = src.readBoolean() ? codeprober.util.JsonUtil.<CompletionItem>readDataArr(src, () -> new CompletionItem(src)) : null;
    this.originContextStart = src.readBoolean() ? src.readInt() : null;
    this.originContextEnd = src.readBoolean() ? src.readInt() : null;
  }

  public static CompleteRes fromJSON(JSONObject obj) {
    return new CompleteRes(
      obj.has("lines") ? (codeprober.util.JsonUtil.<CompletionItem>mapArr(obj.getJSONArray("lines"), (arr13, idx13) -> CompletionItem.fromJSON(arr13.getJSONObject(idx13)))) : null
    , obj.has("originContextStart") ? (obj.getInt("originContextStart")) : null
    , obj.has("originContextEnd") ? (obj.getInt("originContextEnd")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (lines != null) _ret.put("lines", new org.json.JSONArray(lines.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    if (originContextStart != null) _ret.put("originContextStart", originContextStart);
    if (originContextEnd != null) _ret.put("originContextEnd", originContextEnd);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    if (lines != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<CompletionItem>writeDataArr(dst, lines, ent13 -> ent13.writeTo(dst));; } else { dst.writeBoolean(false); }
    if (originContextStart != null) { dst.writeBoolean(true); dst.writeInt(originContextStart);; } else { dst.writeBoolean(false); }
    if (originContextEnd != null) { dst.writeBoolean(true); dst.writeInt(originContextEnd);; } else { dst.writeBoolean(false); }
  }
}
