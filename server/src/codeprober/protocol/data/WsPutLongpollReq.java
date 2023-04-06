package codeprober.protocol.data;

import org.json.JSONObject;

public class WsPutLongpollReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String session;
  public final int etag;
  public WsPutLongpollReq(String session, int etag) {
    this.type = "wsput:longpoll";
    this.session = session;
    this.etag = etag;
  }

  public static WsPutLongpollReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "wsput:longpoll");
    return new WsPutLongpollReq(
      obj.getString("session")
    , obj.getInt("etag")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("session", session);
    _ret.put("etag", etag);
    return _ret;
  }
}
