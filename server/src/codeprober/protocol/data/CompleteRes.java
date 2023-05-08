package codeprober.protocol.data;

import org.json.JSONObject;

public class CompleteRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<String> lines;
  public CompleteRes(java.util.List<String> lines) {
    this.lines = lines;
  }

  public static CompleteRes fromJSON(JSONObject obj) {
    return new CompleteRes(
      obj.has("lines") ? (codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("lines"), (arr, idx) -> arr.getString(idx))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("lines", new org.json.JSONArray(lines));
    return _ret;
  }
}
