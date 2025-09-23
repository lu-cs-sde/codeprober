package codeprober.protocol.data;

import org.json.JSONObject;

public class GetWorkspaceFileReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String path;
  public final Boolean loadMeta;
  public GetWorkspaceFileReq(String path) {
    this(path, (Boolean)null);
  }
  public GetWorkspaceFileReq(String path, Boolean loadMeta) {
    this.type = "GetWorkspaceFile";
    this.path = path;
    this.loadMeta = loadMeta;
  }
  public GetWorkspaceFileReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public GetWorkspaceFileReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "GetWorkspaceFile";
    this.path = src.readUTF();
    this.loadMeta = src.readBoolean() ? src.readBoolean() : null;
  }

  public static GetWorkspaceFileReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "GetWorkspaceFile");
    return new GetWorkspaceFileReq(
      obj.getString("path")
    , obj.has("loadMeta") ? (obj.getBoolean("loadMeta")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("path", path);
    if (loadMeta != null) _ret.put("loadMeta", loadMeta);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(path);
    if (loadMeta != null) { dst.writeBoolean(true); dst.writeBoolean(loadMeta);; } else { dst.writeBoolean(false); }
  }
}
