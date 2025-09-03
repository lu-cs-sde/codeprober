package codeprober.protocol.data;

import org.json.JSONObject;

public class FindWorkspaceFilesReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String query;
  public FindWorkspaceFilesReq(String query) {
    this.type = "FindWorkspaceFiles";
    this.query = query;
  }
  public FindWorkspaceFilesReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public FindWorkspaceFilesReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "FindWorkspaceFiles";
    this.query = src.readUTF();
  }

  public static FindWorkspaceFilesReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "FindWorkspaceFiles");
    return new FindWorkspaceFilesReq(
      obj.getString("query")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("query", query);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(query);
  }
}
