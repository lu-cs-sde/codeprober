package codeprober.protocol.data;

import org.json.JSONObject;

public class PropertyArgCollection implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final java.util.List<PropertyArg> entries;
  public PropertyArgCollection(String type, java.util.List<PropertyArg> entries) {
    this.type = type;
    this.entries = entries;
  }

  public static PropertyArgCollection fromJSON(JSONObject obj) {
    return new PropertyArgCollection(
      obj.getString("type")
    , codeprober.util.JsonUtil.<PropertyArg>mapArr(obj.getJSONArray("entries"), (arr, idx) -> PropertyArg.fromJSON(arr.getJSONObject(idx)))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("entries", new org.json.JSONArray(entries.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
}
