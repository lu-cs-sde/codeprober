package codeprober.protocol.data;

import org.json.JSONObject;

public class ListWorkspaceDirectoryRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<WorkspaceEntry> entries;
  public ListWorkspaceDirectoryRes() {
    this(null);
  }
  public ListWorkspaceDirectoryRes(java.util.List<WorkspaceEntry> entries) {
    this.entries = entries;
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
}
