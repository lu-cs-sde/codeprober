package codeprober.protocol.data;

import org.json.JSONObject;

public class PutWorkspaceContentReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String path;
  public final String content;
  public PutWorkspaceContentReq(String path, String content) {
    this.type = "PutWorkspaceContent";
    this.path = path;
    this.content = content;
  }

  public static PutWorkspaceContentReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "PutWorkspaceContent");
    return new PutWorkspaceContentReq(
      obj.getString("path")
    , obj.getString("content")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("path", path);
    _ret.put("content", content);
    return _ret;
  }
}
