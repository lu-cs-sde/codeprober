package codeprober.protocol.data;

import org.json.JSONObject;

public class Decoration implements codeprober.util.JsonUtil.ToJsonable {
  public final int start;
  public final int end;
  public final String type;
  public final String message;
  public final Integer contextStart;
  public final Integer contextEnd;
  public Decoration(int start, int end, String type, String message, Integer contextStart) {
    this(start, end, type, message, contextStart, (Integer)null);
  }
  public Decoration(int start, int end, String type, String message) {
    this(start, end, type, message, (Integer)null, (Integer)null);
  }
  public Decoration(int start, int end, String type) {
    this(start, end, type, (String)null, (Integer)null, (Integer)null);
  }
  public Decoration(int start, int end, String type, String message, Integer contextStart, Integer contextEnd) {
    this.start = start;
    this.end = end;
    this.type = type;
    this.message = message;
    this.contextStart = contextStart;
    this.contextEnd = contextEnd;
  }
  public Decoration(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public Decoration(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.start = src.readInt();
    this.end = src.readInt();
    this.type = src.readUTF();
    this.message = src.readBoolean() ? src.readUTF() : null;
    this.contextStart = src.readBoolean() ? src.readInt() : null;
    this.contextEnd = src.readBoolean() ? src.readInt() : null;
  }

  public static Decoration fromJSON(JSONObject obj) {
    return new Decoration(
      obj.getInt("start")
    , obj.getInt("end")
    , obj.getString("type")
    , obj.has("message") ? (obj.getString("message")) : null
    , obj.has("contextStart") ? (obj.getInt("contextStart")) : null
    , obj.has("contextEnd") ? (obj.getInt("contextEnd")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("start", start);
    _ret.put("end", end);
    _ret.put("type", type);
    if (message != null) _ret.put("message", message);
    if (contextStart != null) _ret.put("contextStart", contextStart);
    if (contextEnd != null) _ret.put("contextEnd", contextEnd);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeInt(start);
    dst.writeInt(end);
    dst.writeUTF(type);
    if (message != null) { dst.writeBoolean(true); dst.writeUTF(message);; } else { dst.writeBoolean(false); }
    if (contextStart != null) { dst.writeBoolean(true); dst.writeInt(contextStart);; } else { dst.writeBoolean(false); }
    if (contextEnd != null) { dst.writeBoolean(true); dst.writeInt(contextEnd);; } else { dst.writeBoolean(false); }
  }
}
