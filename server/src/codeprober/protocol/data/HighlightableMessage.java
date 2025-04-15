package codeprober.protocol.data;

import org.json.JSONObject;

public class HighlightableMessage implements codeprober.util.JsonUtil.ToJsonable {
  public final int start;
  public final int end;
  public final String msg;
  public HighlightableMessage(int start, int end, String msg) {
    this.start = start;
    this.end = end;
    this.msg = msg;
  }
  public HighlightableMessage(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public HighlightableMessage(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.start = src.readInt();
    this.end = src.readInt();
    this.msg = src.readUTF();
  }

  public static HighlightableMessage fromJSON(JSONObject obj) {
    return new HighlightableMessage(
      obj.getInt("start")
    , obj.getInt("end")
    , obj.getString("msg")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("start", start);
    _ret.put("end", end);
    _ret.put("msg", msg);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeInt(start);
    dst.writeInt(end);
    dst.writeUTF(msg);
  }
}
