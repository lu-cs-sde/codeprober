package codeprober.protocol.data;

import org.json.JSONObject;

public class Property implements codeprober.util.JsonUtil.ToJsonable {
  public final String name;
  public final java.util.List<PropertyArg> args;
  public final String astChildName;
  public final String aspect;
  public Property(String name, java.util.List<PropertyArg> args, String astChildName) {
    this(name, args, astChildName, (String)null);
  }
  public Property(String name, java.util.List<PropertyArg> args) {
    this(name, args, (String)null, (String)null);
  }
  public Property(String name) {
    this(name, (java.util.List<PropertyArg>)null, (String)null, (String)null);
  }
  public Property(String name, java.util.List<PropertyArg> args, String astChildName, String aspect) {
    this.name = name;
    this.args = args;
    this.astChildName = astChildName;
    this.aspect = aspect;
  }
  public Property(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public Property(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.name = src.readUTF();
    this.args = src.readBoolean() ? codeprober.util.JsonUtil.<PropertyArg>readDataArr(src, () -> new PropertyArg(src)) : null;
    this.astChildName = src.readBoolean() ? src.readUTF() : null;
    this.aspect = src.readBoolean() ? src.readUTF() : null;
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
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeUTF(name);
    if (args != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<PropertyArg>writeDataArr(dst, args, ent -> ent.writeTo(dst));; } else { dst.writeBoolean(false); }
    if (astChildName != null) { dst.writeBoolean(true); dst.writeUTF(astChildName);; } else { dst.writeBoolean(false); }
    if (aspect != null) { dst.writeBoolean(true); dst.writeUTF(aspect);; } else { dst.writeBoolean(false); }
  }
}
