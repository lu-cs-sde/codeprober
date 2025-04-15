package codeprober.protocol.data;

import org.json.JSONObject;

public class PutWorkspaceMetadataReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String path;
  public final org.json.JSONObject metadata;
  public PutWorkspaceMetadataReq(String path) {
    this(path, (org.json.JSONObject)null);
  }
  public PutWorkspaceMetadataReq(String path, org.json.JSONObject metadata) {
    this.type = "PutWorkspaceMetadata";
    this.path = path;
    this.metadata = metadata;
  }
  public PutWorkspaceMetadataReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public PutWorkspaceMetadataReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "PutWorkspaceMetadata";
    this.path = src.readUTF();
    this.metadata = src.readBoolean() ? new org.json.JSONObject(src.readUTF()) : null;
  }

  public static PutWorkspaceMetadataReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "PutWorkspaceMetadata");
    return new PutWorkspaceMetadataReq(
      obj.getString("path")
    , obj.has("metadata") ? (obj.getJSONObject("metadata")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("path", path);
    if (metadata != null) _ret.put("metadata", metadata);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(path);
    if (metadata != null) { dst.writeBoolean(true); dst.writeUTF(metadata.toString());; } else { dst.writeBoolean(false); }
  }
}
