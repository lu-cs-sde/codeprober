package codeprober.protocol.data;

import org.json.JSONObject;

public class WorkspacePathsUpdated implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final java.util.List<String> paths;
  public WorkspacePathsUpdated(java.util.List<String> paths) {
    this.type = "workspace_paths_updated";
    this.paths = paths;
  }
  public WorkspacePathsUpdated(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public WorkspacePathsUpdated(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "workspace_paths_updated";
    this.paths = codeprober.util.JsonUtil.<String>readDataArr(src, () -> src.readUTF());
  }

  public static WorkspacePathsUpdated fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "workspace_paths_updated");
    return new WorkspacePathsUpdated(
      codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("paths"), (arr1, idx1) -> arr1.getString(idx1))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("paths", new org.json.JSONArray(paths));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    codeprober.util.JsonUtil.<String>writeDataArr(dst, paths, ent1 -> dst.writeUTF(ent1));
  }
}
