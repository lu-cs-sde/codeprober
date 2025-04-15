package codeprober.protocol.data;

import org.json.JSONObject;

public class ListWorkspaceDirectoryReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String path;
  public ListWorkspaceDirectoryReq() {
    this((String)null);
  }
  public ListWorkspaceDirectoryReq(String path) {
    this.type = "ListWorkspaceDirectory";
    this.path = path;
  }
  public ListWorkspaceDirectoryReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public ListWorkspaceDirectoryReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "ListWorkspaceDirectory";
    this.path = src.readBoolean() ? src.readUTF() : null;
  }

  public static ListWorkspaceDirectoryReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "ListWorkspaceDirectory");
    return new ListWorkspaceDirectoryReq(
      obj.has("path") ? (obj.getString("path")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    if (path != null) _ret.put("path", path);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    if (path != null) { dst.writeBoolean(true); dst.writeUTF(path);; } else { dst.writeBoolean(false); }
  }
}
