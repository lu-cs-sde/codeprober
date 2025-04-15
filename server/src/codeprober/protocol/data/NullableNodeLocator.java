package codeprober.protocol.data;

import org.json.JSONObject;

public class NullableNodeLocator implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final NodeLocator value;
  public NullableNodeLocator(String type) {
    this(type, (NodeLocator)null);
  }
  public NullableNodeLocator(String type, NodeLocator value) {
    this.type = type;
    this.value = value;
  }
  public NullableNodeLocator(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public NullableNodeLocator(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = src.readUTF();
    this.value = src.readBoolean() ? new NodeLocator(src) : null;
  }

  public static NullableNodeLocator fromJSON(JSONObject obj) {
    return new NullableNodeLocator(
      obj.getString("type")
    , obj.has("value") ? (NodeLocator.fromJSON(obj.getJSONObject("value"))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    if (value != null) _ret.put("value", value.toJSON());
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeUTF(type);
    if (value != null) { dst.writeBoolean(true); value.writeTo(dst);; } else { dst.writeBoolean(false); }
  }
}
