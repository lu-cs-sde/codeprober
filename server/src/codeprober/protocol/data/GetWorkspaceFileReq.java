package codeprober.protocol.data;

import org.json.JSONObject;

public class GetWorkspaceFileReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String path;
  public GetWorkspaceFileReq(String path) {
    this.type = "GetWorkspaceFile";
    this.path = path;
  }

  public static GetWorkspaceFileReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "GetWorkspaceFile");
    return new GetWorkspaceFileReq(
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
