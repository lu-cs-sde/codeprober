package codeprober.protocol.data;

import org.json.JSONObject;

public class PropertyArgCollection implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final java.util.List<PropertyArg> entries;
  public PropertyArgCollection(String type, java.util.List<PropertyArg> entries) {
    this.type = type;
    this.entries = entries;
  }
  public PropertyArgCollection(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public PropertyArgCollection(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = src.readUTF();
    this.entries = codeprober.util.JsonUtil.<PropertyArg>readDataArr(src, () -> new PropertyArg(src));
  }

  public static PropertyArgCollection fromJSON(JSONObject obj) {
    return new PropertyArgCollection(
      obj.getString("type")
    , codeprober.util.JsonUtil.<PropertyArg>mapArr(obj.getJSONArray("entries"), (arr24, idx24) -> PropertyArg.fromJSON(arr24.getJSONObject(idx24)))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("entries", new org.json.JSONArray(entries.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeUTF(type);
    codeprober.util.JsonUtil.<PropertyArg>writeDataArr(dst, entries, ent24 -> ent24.writeTo(dst));
  }
}
