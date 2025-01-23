package codeprober.protocol.data;

import org.json.JSONObject;

public class StopJobReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final int job;
  public StopJobReq(int job) {
    this.type = "Concurrent:StopJob";
    this.job = job;
  }
  public StopJobReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public StopJobReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "Concurrent:StopJob";
    this.job = src.readInt();
  }

  public static StopJobReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Concurrent:StopJob");
    return new StopJobReq(
      obj.getInt("job")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("job", job);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeInt(job);
  }
}
