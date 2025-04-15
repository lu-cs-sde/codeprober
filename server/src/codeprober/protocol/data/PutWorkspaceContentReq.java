package codeprober.protocol.data;

import org.json.JSONObject;

public class PutWorkspaceContentReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String path;
  public final String content;
  public PutWorkspaceContentReq(String path, String content) {
    this.type = "PutWorkspaceContent";
    this.path = path;
    this.content = content;
  }
  public PutWorkspaceContentReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public PutWorkspaceContentReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "PutWorkspaceContent";
    this.path = src.readUTF();
    this.content = src.readUTF();
  }

  public static PutWorkspaceContentReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "PutWorkspaceContent");
    return new PutWorkspaceContentReq(
      obj.getString("path")
    , obj.getString("content")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("path", path);
    _ret.put("content", content);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(path);
    dst.writeUTF(content);
  }
}
