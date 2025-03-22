package codeprober.protocol.data;

import org.json.JSONObject;

public class UnlinkWorkspacePathReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String path;
  public UnlinkWorkspacePathReq(String path) {
    this.type = "UnlinkWorkspacePath";
    this.path = path;
  }

  public static UnlinkWorkspacePathReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "UnlinkWorkspacePath");
    return new UnlinkWorkspacePathReq(
      obj.getString("path")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("path", path);
    return _ret;
  }
}
