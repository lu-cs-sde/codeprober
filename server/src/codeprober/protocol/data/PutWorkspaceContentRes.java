package codeprober.protocol.data;

import org.json.JSONObject;

public class PutWorkspaceContentRes implements codeprober.util.JsonUtil.ToJsonable {
  public final boolean ok;
  public PutWorkspaceContentRes(boolean ok) {
    this.ok = ok;
  }

  public static PutWorkspaceContentRes fromJSON(JSONObject obj) {
    return new PutWorkspaceContentRes(
      obj.getBoolean("ok")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("ok", ok);
    return _ret;
  }
}
