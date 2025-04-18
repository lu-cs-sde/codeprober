// Automatically generated by protocolgen.GenJava. DO NOT MODIFY
package codeprober.protocol.data;

import org.json.JSONObject;

public class LongPollResponse implements codeprober.util.JsonUtil.ToJsonable {
  public static enum Type {
    etag,
    push,
  }
  private static final Type[] typeValues = Type.values();

  public final Type type;
  public final Object value;
  private LongPollResponse(Type type, Object value) {
    this.type = type;
    this.value = value;
  }
  public LongPollResponse(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public LongPollResponse(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = typeValues[src.readInt()];
    switch (this.type) {
    case etag:
        this.value = src.readInt();
        break;
    case push:
    default:
        this.value = new org.json.JSONObject(src.readUTF());
        break;
    }
  }
  public static LongPollResponse fromEtag(int val) { return new LongPollResponse(Type.etag, val); }
  public static LongPollResponse fromPush(org.json.JSONObject val) { return new LongPollResponse(Type.push, val); }

  public boolean isEtag() { return type == Type.etag; }
  public int asEtag() { if (type != Type.etag) { throw new IllegalStateException("This LongPollResponse is not of type etag, it is '" + type + "'"); } return (int)value; }
  public boolean isPush() { return type == Type.push; }
  public org.json.JSONObject asPush() { if (type != Type.push) { throw new IllegalStateException("This LongPollResponse is not of type push, it is '" + type + "'"); } return (org.json.JSONObject)value; }

  public static LongPollResponse fromJSON(JSONObject obj) {
    final Type type;
    try { type = Type.valueOf(obj.getString("type")); }
    catch (IllegalArgumentException e) { throw new org.json.JSONException(e); }
    switch (type) {
    case etag:
      try {
        final int val = obj.getInt("value");
        return fromEtag(val);
      } catch (org.json.JSONException e) {
        throw new org.json.JSONException("Not a valid LongPollResponse", e);
      }
    case push:
    default:
      try {
        final org.json.JSONObject val = obj.getJSONObject("value");
        return fromPush(val);
      } catch (org.json.JSONException e) {
        throw new org.json.JSONException("Not a valid LongPollResponse", e);
      }
    }
  }

  public JSONObject toJSON() {
    final JSONObject ret = new JSONObject().put("type", type.name());
    switch (type) {
    case etag:
      ret.put("value", ((int)value));
      break;
    case push:
    default:
      ret.put("value", ((org.json.JSONObject)value));
      break;
    }
    return ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeInt(type.ordinal());
    switch (type) {
    case etag:
      dst.writeInt(((int)value));
      break;
    case push:
    default:
      dst.writeUTF(((org.json.JSONObject)value).toString());
      break;
    }
  }
}
