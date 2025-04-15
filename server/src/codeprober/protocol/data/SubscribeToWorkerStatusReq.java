package codeprober.protocol.data;

import org.json.JSONObject;

public class SubscribeToWorkerStatusReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final int job;
  public SubscribeToWorkerStatusReq(int job) {
    this.type = "Concurrent:SubscribeToWorkerStatus";
    this.job = job;
  }
  public SubscribeToWorkerStatusReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public SubscribeToWorkerStatusReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "Concurrent:SubscribeToWorkerStatus";
    this.job = src.readInt();
  }

  public static SubscribeToWorkerStatusReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Concurrent:SubscribeToWorkerStatus");
    return new SubscribeToWorkerStatusReq(
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
