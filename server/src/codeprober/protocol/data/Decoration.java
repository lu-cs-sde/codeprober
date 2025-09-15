package codeprober.protocol.data;

import org.json.JSONObject;

public class Decoration implements codeprober.util.JsonUtil.ToJsonable {
  public final int start;
  public final int end;
  public final String type;
  public final String message;
  public Decoration(int start, int end, String type) {
    this(start, end, type, (String)null);
  }
  public Decoration(int start, int end, String type, String message) {
    this.start = start;
    this.end = end;
    this.type = type;
    this.message = message;
  }
  public Decoration(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public Decoration(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.start = src.readInt();
    this.end = src.readInt();
    this.type = src.readUTF();
    this.message = src.readBoolean() ? src.readUTF() : null;
  }

  public static Decoration fromJSON(JSONObject obj) {
    return new Decoration(
      obj.getInt("start")
    , obj.getInt("end")
    , obj.getString("type")
    , obj.has("message") ? (obj.getString("message")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("start", start);
    _ret.put("end", end);
    _ret.put("type", type);
    if (message != null) _ret.put("message", message);
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
  }
}
