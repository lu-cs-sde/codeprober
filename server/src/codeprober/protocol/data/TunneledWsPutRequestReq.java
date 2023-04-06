package codeprober.protocol.data;

import org.json.JSONObject;

public class TunneledWsPutRequestReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String session;
  public final org.json.JSONObject request;
  public TunneledWsPutRequestReq(String session, org.json.JSONObject request) {
    this.type = "wsput:tunnel";
    this.session = session;
    this.request = request;
  }

  public static TunneledWsPutRequestReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "wsput:tunnel");
    return new TunneledWsPutRequestReq(
      obj.getString("session")
    , obj.getJSONObject("request")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("session", session);
    _ret.put("request", request);
    return _ret;
  }
}
