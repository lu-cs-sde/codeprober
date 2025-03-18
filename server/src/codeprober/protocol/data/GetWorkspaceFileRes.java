package codeprober.protocol.data;

import org.json.JSONObject;

public class GetWorkspaceFileRes implements codeprober.util.JsonUtil.ToJsonable {
  public final String content;
  public final org.json.JSONObject metadata;
  public GetWorkspaceFileRes(String content) {
    this(content, null);
  }
  public GetWorkspaceFileRes() {
    this(null, null);
  }
  public GetWorkspaceFileRes(String content, org.json.JSONObject metadata) {
    this.content = content;
    this.metadata = metadata;
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
}
