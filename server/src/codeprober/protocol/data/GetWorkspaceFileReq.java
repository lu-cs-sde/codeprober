package codeprober.protocol.data;

import org.json.JSONObject;

public class GetWorkspaceFileReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String path;
  public GetWorkspaceFileReq(String path) {
    this.type = "GetWorkspaceFile";
    this.path = path;
  }
  public GetWorkspaceFileReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public GetWorkspaceFileReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "GetWorkspaceFile";
    this.path = src.readUTF();
  }

  public static GetWorkspaceFileReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "GetWorkspaceFile");
    return new GetWorkspaceFileReq(
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
