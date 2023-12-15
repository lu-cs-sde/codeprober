package codeprober.protocol.data;

import org.json.JSONObject;

public class Property implements codeprober.util.JsonUtil.ToJsonable {
  public final String name;
  public final java.util.List<PropertyArg> args;
  public final String astChildName;
  public final String aspect;
  public Property(String name, java.util.List<PropertyArg> args, String astChildName) {
    this(name, args, astChildName, null);
  }
  public Property(String name, java.util.List<PropertyArg> args) {
    this(name, args, null, null);
  }
  public Property(String name) {
    this(name, null, null, null);
  }
  public Property(String name, java.util.List<PropertyArg> args, String astChildName, String aspect) {
    this.name = name;
    this.args = args;
    this.astChildName = astChildName;
    this.aspect = aspect;
  }

  public static Property fromJSON(JSONObject obj) {
    return new Property(
      obj.getString("name")
    , obj.has("args") ? (codeprober.util.JsonUtil.<PropertyArg>mapArr(obj.getJSONArray("args"), (arr, idx) -> PropertyArg.fromJSON(arr.getJSONObject(idx)))) : null
    , obj.has("astChildName") ? (obj.getString("astChildName")) : null
    , obj.has("aspect") ? (obj.getString("aspect")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("name", name);
    if (args != null) _ret.put("args", new org.json.JSONArray(args.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    if (astChildName != null) _ret.put("astChildName", astChildName);
    if (aspect != null) _ret.put("aspect", aspect);
    return _ret;
  }
}
