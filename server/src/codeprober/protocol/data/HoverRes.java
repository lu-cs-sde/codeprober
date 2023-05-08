package codeprober.protocol.data;

import org.json.JSONObject;

public class HoverRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<String> lines;
  public HoverRes(java.util.List<String> lines) {
    this.lines = lines;
  }

  public static HoverRes fromJSON(JSONObject obj) {
    return new HoverRes(
      obj.has("lines") ? (codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("lines"), (arr, idx) -> arr.getString(idx))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("lines", new org.json.JSONArray(lines));
    return _ret;
  }
}
