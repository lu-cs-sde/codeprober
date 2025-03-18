package codeprober.protocol.data;

import org.json.JSONObject;

public class UnlinkWorkspacePathRes implements codeprober.util.JsonUtil.ToJsonable {
  public final boolean ok;
  public UnlinkWorkspacePathRes(boolean ok) {
    this.ok = ok;
  }

  public static UnlinkWorkspacePathRes fromJSON(JSONObject obj) {
    return new UnlinkWorkspacePathRes(
      obj.getBoolean("ok")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("ok", ok);
    return _ret;
  }
}
