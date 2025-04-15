package codeprober.protocol.data;

import org.json.JSONObject;

public class SubscribeToWorkerStatusRes implements codeprober.util.JsonUtil.ToJsonable {
  public final int subscriberId;
  public SubscribeToWorkerStatusRes(int subscriberId) {
    this.subscriberId = subscriberId;
  }
  public SubscribeToWorkerStatusRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public SubscribeToWorkerStatusRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.subscriberId = src.readInt();
  }

  public static SubscribeToWorkerStatusRes fromJSON(JSONObject obj) {
    return new SubscribeToWorkerStatusRes(
      obj.getInt("subscriberId")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("subscriberId", subscriberId);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeInt(subscriberId);
  }
}
