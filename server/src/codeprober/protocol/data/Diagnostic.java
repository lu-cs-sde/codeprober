package codeprober.protocol.data;

import org.json.JSONObject;

public class Diagnostic implements codeprober.util.JsonUtil.ToJsonable {
  public final codeprober.protocol.DiagnosticType type;
  public final int start;
  public final int end;
  public final String msg;
  public Diagnostic(codeprober.protocol.DiagnosticType type, int start, int end, String msg) {
    this.type = type;
    this.start = start;
    this.end = end;
    this.msg = msg;
  }
  public Diagnostic(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public Diagnostic(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = codeprober.protocol.DiagnosticType.values()[src.readInt()];
    this.start = src.readInt();
    this.end = src.readInt();
    this.msg = src.readUTF();
  }

  public static Diagnostic fromJSON(JSONObject obj) {
    return new Diagnostic(
      codeprober.protocol.DiagnosticType.parseFromJson(obj.getString("type"))
    , obj.getInt("start")
    , obj.getInt("end")
    , obj.getString("msg")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type.name());
    _ret.put("start", start);
    _ret.put("end", end);
    _ret.put("msg", msg);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeInt(type.ordinal());
    dst.writeInt(start);
    dst.writeInt(end);
    dst.writeUTF(msg);
  }
}
