package codeprober.protocol.data;

import org.json.JSONObject;

public class BackingFileUpdated implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String contents;
  public BackingFileUpdated(String contents) {
    this.type = "backing_file_update";
    this.contents = contents;
  }
  public BackingFileUpdated(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public BackingFileUpdated(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "backing_file_update";
    this.contents = src.readUTF();
  }

  public static BackingFileUpdated fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "backing_file_update");
    return new BackingFileUpdated(
      obj.getString("contents")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("contents", contents);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(contents);
  }
}
