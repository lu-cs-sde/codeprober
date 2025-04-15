package codeprober.protocol.data;

import org.json.JSONObject;

public class UnlinkWorkspacePathReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String path;
  public UnlinkWorkspacePathReq(String path) {
    this.type = "UnlinkWorkspacePath";
    this.path = path;
  }
  public UnlinkWorkspacePathReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public UnlinkWorkspacePathReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "UnlinkWorkspacePath";
    this.path = src.readUTF();
  }

  public static UnlinkWorkspacePathReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "UnlinkWorkspacePath");
    return new UnlinkWorkspacePathReq(
      obj.getString("path")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("path", path);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(path);
  }
}
