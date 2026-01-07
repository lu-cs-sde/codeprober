package codeprober.protocol.data;

import org.json.JSONObject;

public class FindWorkspaceFilesRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<String> matches;
  public final Boolean truncatedSearch;
  public FindWorkspaceFilesRes(java.util.List<String> matches) {
    this(matches, (Boolean)null);
  }
  public FindWorkspaceFilesRes() {
    this((java.util.List<String>)null, (Boolean)null);
  }
  public FindWorkspaceFilesRes(java.util.List<String> matches, Boolean truncatedSearch) {
    this.matches = matches;
    this.truncatedSearch = truncatedSearch;
  }
  public FindWorkspaceFilesRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public FindWorkspaceFilesRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.matches = src.readBoolean() ? codeprober.util.JsonUtil.<String>readDataArr(src, () -> src.readUTF()) : null;
    this.truncatedSearch = src.readBoolean() ? src.readBoolean() : null;
  }

  public static FindWorkspaceFilesRes fromJSON(JSONObject obj) {
    return new FindWorkspaceFilesRes(
      obj.has("matches") ? (codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("matches"), (arr1, idx1) -> arr1.getString(idx1))) : null
    , obj.has("truncatedSearch") ? (obj.getBoolean("truncatedSearch")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (matches != null) _ret.put("matches", new org.json.JSONArray(matches));
    if (truncatedSearch != null) _ret.put("truncatedSearch", truncatedSearch);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    if (matches != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<String>writeDataArr(dst, matches, ent1 -> dst.writeUTF(ent1));; } else { dst.writeBoolean(false); }
    if (truncatedSearch != null) { dst.writeBoolean(true); dst.writeBoolean(truncatedSearch);; } else { dst.writeBoolean(false); }
  }
}
