package codeprober.protocol.data;

import org.json.JSONObject;

public class RenameWorkspacePathRes implements codeprober.util.JsonUtil.ToJsonable {
  public final boolean ok;
  public RenameWorkspacePathRes(boolean ok) {
    this.ok = ok;
  }

  public static RenameWorkspacePathRes fromJSON(JSONObject obj) {
    return new RenameWorkspacePathRes(
      obj.getBoolean("ok")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("ok", ok);
    return _ret;
  }
}
