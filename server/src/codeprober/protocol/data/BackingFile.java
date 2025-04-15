package codeprober.protocol.data;

import org.json.JSONObject;

public class BackingFile implements codeprober.util.JsonUtil.ToJsonable {
  public final String path;
  public final String value;
  public BackingFile(String path, String value) {
    this.path = path;
    this.value = value;
  }
  public BackingFile(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public BackingFile(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.path = src.readUTF();
    this.value = src.readUTF();
  }

  public static BackingFile fromJSON(JSONObject obj) {
    return new BackingFile(
      obj.getString("path")
    , obj.getString("value")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("path", path);
    _ret.put("value", value);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeUTF(path);
    dst.writeUTF(value);
  }
}
