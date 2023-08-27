package codeprober.protocol.data;

import org.json.JSONObject;

public class WsPutLongpollRes implements codeprober.util.JsonUtil.ToJsonable {
  public final LongPollResponse data;
  public WsPutLongpollRes() {
    this(null);
  }
  public WsPutLongpollRes(LongPollResponse data) {
    this.data = data;
  }

  public static WsPutLongpollRes fromJSON(JSONObject obj) {
    return new WsPutLongpollRes(
      obj.has("data") ? (LongPollResponse.fromJSON(obj.getJSONObject("data"))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (data != null) _ret.put("data", data.toJSON());
    return _ret;
  }
}
