package codeprober.protocol.data;

import org.json.JSONObject;

public class SubmitWorkerTaskReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final long job;
  public final org.json.JSONObject data;
  public SubmitWorkerTaskReq(long job, org.json.JSONObject data) {
    this.type = "Concurrent:SubmitTask";
    this.job = job;
    this.data = data;
  }
  public SubmitWorkerTaskReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public SubmitWorkerTaskReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "Concurrent:SubmitTask";
    this.job = src.readLong();
    this.data = new org.json.JSONObject(src.readUTF());
  }

  public static SubmitWorkerTaskReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Concurrent:SubmitTask");
    return new SubmitWorkerTaskReq(
      obj.getLong("job")
    , obj.getJSONObject("data")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("job", job);
    _ret.put("data", data);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeLong(job);
    dst.writeUTF(data.toString());
  }
}
