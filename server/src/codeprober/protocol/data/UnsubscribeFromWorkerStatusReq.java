package codeprober.protocol.data;

import org.json.JSONObject;

public class UnsubscribeFromWorkerStatusReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final int job;
  public final int subscriberId;
  public UnsubscribeFromWorkerStatusReq(int job, int subscriberId) {
    this.type = "Concurrent:UnsubscribeFromWorkerStatus";
    this.job = job;
    this.subscriberId = subscriberId;
  }
  public UnsubscribeFromWorkerStatusReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public UnsubscribeFromWorkerStatusReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "Concurrent:UnsubscribeFromWorkerStatus";
    this.job = src.readInt();
    this.subscriberId = src.readInt();
  }

  public static UnsubscribeFromWorkerStatusReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Concurrent:UnsubscribeFromWorkerStatus");
    return new UnsubscribeFromWorkerStatusReq(
      obj.getInt("job")
    , obj.getInt("subscriberId")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("job", job);
    _ret.put("subscriberId", subscriberId);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeInt(job);
    dst.writeInt(subscriberId);
  }
}
