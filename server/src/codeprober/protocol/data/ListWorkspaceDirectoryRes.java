package codeprober.protocol.data;

import org.json.JSONObject;

public class ListWorkspaceDirectoryRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<WorkspaceEntry> entries;
  public ListWorkspaceDirectoryRes() {
    this((java.util.List<WorkspaceEntry>)null);
  }
  public ListWorkspaceDirectoryRes(java.util.List<WorkspaceEntry> entries) {
    this.entries = entries;
  }
  public ListWorkspaceDirectoryRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public ListWorkspaceDirectoryRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.entries = src.readBoolean() ? codeprober.util.JsonUtil.<WorkspaceEntry>readDataArr(src, () -> new WorkspaceEntry(src)) : null;
  }

  public static ListWorkspaceDirectoryRes fromJSON(JSONObject obj) {
    return new ListWorkspaceDirectoryRes(
      obj.has("entries") ? (codeprober.util.JsonUtil.<WorkspaceEntry>mapArr(obj.getJSONArray("entries"), (arr, idx) -> WorkspaceEntry.fromJSON(arr.getJSONObject(idx)))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (entries != null) _ret.put("entries", new org.json.JSONArray(entries.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    if (entries != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<WorkspaceEntry>writeDataArr(dst, entries, ent -> ent.writeTo(dst));; } else { dst.writeBoolean(false); }
  }
}
