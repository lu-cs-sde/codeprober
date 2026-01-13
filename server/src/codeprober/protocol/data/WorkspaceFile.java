package codeprober.protocol.data;

import org.json.JSONObject;

public class WorkspaceFile implements codeprober.util.JsonUtil.ToJsonable {
  public final String name;
  public final Boolean readOnly;
  public WorkspaceFile(String name) {
    this(name, (Boolean)null);
  }
  public WorkspaceFile(String name, Boolean readOnly) {
    this.name = name;
    this.readOnly = readOnly;
  }
  public WorkspaceFile(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public WorkspaceFile(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.name = src.readUTF();
    this.readOnly = src.readBoolean() ? src.readBoolean() : null;
  }

  public static WorkspaceFile fromJSON(JSONObject obj) {
    return new WorkspaceFile(
      obj.getString("name")
    , obj.has("readOnly") ? (obj.getBoolean("readOnly")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("name", name);
    if (readOnly != null) _ret.put("readOnly", readOnly);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeUTF(name);
    if (readOnly != null) { dst.writeBoolean(true); dst.writeBoolean(readOnly);; } else { dst.writeBoolean(false); }
  }
}
