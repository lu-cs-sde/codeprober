package codeprober.protocol.data;

import org.json.JSONObject;

public class WsPutInitReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String session;
  public WsPutInitReq(String session) {
    this.type = "wsput:init";
    this.session = session;
  }

  public static WsPutInitReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "wsput:init");
    return new WsPutInitReq(
      obj.getString("session")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("session", session);
    return _ret;
  }
}
