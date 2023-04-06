package codeprober.protocol.data;

import org.json.JSONObject;

public class Property implements codeprober.util.JsonUtil.ToJsonable {
  public final String name;
  public final java.util.List<PropertyArg> args;
  public final String astChildName;
  public Property(String name, java.util.List<PropertyArg> args, String astChildName) {
    this.name = name;
    this.args = args;
    this.astChildName = astChildName;
  }

  public static Property fromJSON(JSONObject obj) {
    return new Property(
      obj.getString("name")
    , obj.has("args") ? (codeprober.util.JsonUtil.<PropertyArg>mapArr(obj.getJSONArray("args"), (arr, idx) -> PropertyArg.fromJSON(arr.getJSONObject(idx)))) : null
    , obj.has("astChildName") ? (obj.getString("astChildName")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("name", name);
    if (args != null) _ret.put("args", new org.json.JSONArray(args.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    if (astChildName != null) _ret.put("astChildName", astChildName);
    return _ret;
  }
}
