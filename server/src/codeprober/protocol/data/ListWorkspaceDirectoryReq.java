package codeprober.protocol.data;

import org.json.JSONObject;

public class ListWorkspaceDirectoryReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String path;
  public ListWorkspaceDirectoryReq() {
    this(null);
  }
  public ListWorkspaceDirectoryReq(String path) {
    this.type = "ListWorkspaceDirectory";
    this.path = path;
  }

  public static ListWorkspaceDirectoryReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "ListWorkspaceDirectory");
    return new ListWorkspaceDirectoryReq(
      obj.has("path") ? (obj.getString("path")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    if (path != null) _ret.put("path", path);
    return _ret;
  }
}
