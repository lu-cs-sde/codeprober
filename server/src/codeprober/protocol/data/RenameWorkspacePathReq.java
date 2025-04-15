package codeprober.protocol.data;

import org.json.JSONObject;

public class RenameWorkspacePathReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String srcPath;
  public final String dstPath;
  public RenameWorkspacePathReq(String srcPath, String dstPath) {
    this.type = "RenameWorkspacePath";
    this.srcPath = srcPath;
    this.dstPath = dstPath;
  }
  public RenameWorkspacePathReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public RenameWorkspacePathReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "RenameWorkspacePath";
    this.srcPath = src.readUTF();
    this.dstPath = src.readUTF();
  }

  public static RenameWorkspacePathReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "RenameWorkspacePath");
    return new RenameWorkspacePathReq(
      obj.getString("srcPath")
    , obj.getString("dstPath")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("srcPath", srcPath);
    _ret.put("dstPath", dstPath);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(srcPath);
    dst.writeUTF(dstPath);
  }
}
