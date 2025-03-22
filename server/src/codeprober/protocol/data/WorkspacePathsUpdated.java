package codeprober.protocol.data;

import org.json.JSONObject;

public class WorkspacePathsUpdated implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final java.util.List<String> paths;
  public WorkspacePathsUpdated(java.util.List<String> paths) {
    this.type = "workspace_paths_updated";
    this.paths = paths;
  }

  public static WorkspacePathsUpdated fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "workspace_paths_updated");
    return new WorkspacePathsUpdated(
      codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("paths"), (arr, idx) -> arr.getString(idx))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("paths", new org.json.JSONArray(paths));
    return _ret;
  }
}
