package codeprober.protocol.data;

import org.json.JSONObject;

public class WsPutInitRes implements codeprober.util.JsonUtil.ToJsonable {
  public final InitInfo info;
  public WsPutInitRes(InitInfo info) {
    this.info = info;
  }

  public static WsPutInitRes fromJSON(JSONObject obj) {
    return new WsPutInitRes(
      InitInfo.fromJSON(obj.getJSONObject("info"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("info", info.toJSON());
    return _ret;
  }
}
