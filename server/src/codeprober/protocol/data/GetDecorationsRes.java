package codeprober.protocol.data;

import org.json.JSONObject;

public class GetDecorationsRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<Decoration> lines;
  public GetDecorationsRes() {
    this((java.util.List<Decoration>)null);
  }
  public GetDecorationsRes(java.util.List<Decoration> lines) {
    this.lines = lines;
  }
  public GetDecorationsRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public GetDecorationsRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.lines = src.readBoolean() ? codeprober.util.JsonUtil.<Decoration>readDataArr(src, () -> new Decoration(src)) : null;
  }

  public static GetDecorationsRes fromJSON(JSONObject obj) {
    return new GetDecorationsRes(
      obj.has("lines") ? (codeprober.util.JsonUtil.<Decoration>mapArr(obj.getJSONArray("lines"), (arr, idx) -> Decoration.fromJSON(arr.getJSONObject(idx)))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (lines != null) _ret.put("lines", new org.json.JSONArray(lines.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    if (lines != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<Decoration>writeDataArr(dst, lines, ent -> ent.writeTo(dst));; } else { dst.writeBoolean(false); }
  }
}
