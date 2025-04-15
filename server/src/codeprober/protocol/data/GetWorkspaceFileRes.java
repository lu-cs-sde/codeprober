package codeprober.protocol.data;

import org.json.JSONObject;

public class GetWorkspaceFileRes implements codeprober.util.JsonUtil.ToJsonable {
  public final String content;
  public final org.json.JSONObject metadata;
  public GetWorkspaceFileRes(String content) {
    this(content, (org.json.JSONObject)null);
  }
  public GetWorkspaceFileRes() {
    this((String)null, (org.json.JSONObject)null);
  }
  public GetWorkspaceFileRes(String content, org.json.JSONObject metadata) {
    this.content = content;
    this.metadata = metadata;
  }
  public GetWorkspaceFileRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public GetWorkspaceFileRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.content = src.readBoolean() ? src.readUTF() : null;
    this.metadata = src.readBoolean() ? new org.json.JSONObject(src.readUTF()) : null;
  }

  public static GetWorkspaceFileRes fromJSON(JSONObject obj) {
    return new GetWorkspaceFileRes(
      obj.has("content") ? (obj.getString("content")) : null
    , obj.has("metadata") ? (obj.getJSONObject("metadata")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (content != null) _ret.put("content", content);
    if (metadata != null) _ret.put("metadata", metadata);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    if (content != null) { dst.writeBoolean(true); dst.writeUTF(content);; } else { dst.writeBoolean(false); }
    if (metadata != null) { dst.writeBoolean(true); dst.writeUTF(metadata.toString());; } else { dst.writeBoolean(false); }
  }
}
