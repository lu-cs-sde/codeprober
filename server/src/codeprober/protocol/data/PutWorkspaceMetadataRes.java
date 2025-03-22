package codeprober.protocol.data;

import org.json.JSONObject;

public class PutWorkspaceMetadataRes implements codeprober.util.JsonUtil.ToJsonable {
  public final boolean ok;
  public PutWorkspaceMetadataRes(boolean ok) {
    this.ok = ok;
  }

  public static PutWorkspaceMetadataRes fromJSON(JSONObject obj) {
    return new PutWorkspaceMetadataRes(
      obj.getBoolean("ok")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("ok", ok);
    return _ret;
  }
}
