package codeprober.protocol.data;

import org.json.JSONObject;

public class TunneledWsPutRequestRes implements codeprober.util.JsonUtil.ToJsonable {
  public final org.json.JSONObject response;
  public TunneledWsPutRequestRes(org.json.JSONObject response) {
    this.response = response;
  }

  public static TunneledWsPutRequestRes fromJSON(JSONObject obj) {
    return new TunneledWsPutRequestRes(
      obj.getJSONObject("response")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("response", response);
    return _ret;
  }
}
