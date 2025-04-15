package codeprober.protocol.data;

import org.json.JSONObject;

public class AsyncRpcUpdate implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final long job;
  public final boolean isFinalUpdate;
  public final AsyncRpcUpdateValue value;
  public AsyncRpcUpdate(long job, boolean isFinalUpdate, AsyncRpcUpdateValue value) {
    this.type = "asyncUpdate";
    this.job = job;
    this.isFinalUpdate = isFinalUpdate;
    this.value = value;
  }
  public AsyncRpcUpdate(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public AsyncRpcUpdate(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "asyncUpdate";
    this.job = src.readLong();
    this.isFinalUpdate = src.readBoolean();
    this.value = new AsyncRpcUpdateValue(src);
  }

  public static AsyncRpcUpdate fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "asyncUpdate");
    return new AsyncRpcUpdate(
      obj.getLong("job")
    , obj.getBoolean("isFinalUpdate")
    , AsyncRpcUpdateValue.fromJSON(obj.getJSONObject("value"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("job", job);
    _ret.put("isFinalUpdate", isFinalUpdate);
    _ret.put("value", value.toJSON());
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeLong(job);
    dst.writeBoolean(isFinalUpdate);
    value.writeTo(dst);
  }
}
