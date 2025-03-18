package codeprober.protocol.data;

import org.json.JSONObject;

public class PutWorkspaceMetadataReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String path;
  public final org.json.JSONObject metadata;
  public PutWorkspaceMetadataReq(String path) {
    this(path, null);
  }
  public PutWorkspaceMetadataReq(String path, org.json.JSONObject metadata) {
    this.type = "PutWorkspaceMetadata";
    this.path = path;
    this.metadata = metadata;
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
}
