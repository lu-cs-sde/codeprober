package codeprober.protocol.data;

import org.json.JSONObject;

public class AsyncRequestReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final org.json.JSONObject src;
  public final Long job;
  public AsyncRequestReq(org.json.JSONObject src) {
    this(src, (Long)null);
  }
  public AsyncRequestReq(org.json.JSONObject src, Long job) {
    this.type = "AsyncRequest";
    this.src = src;
    this.job = job;
  }
  public AsyncRequestReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public AsyncRequestReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "AsyncRequest";
    this.src = new org.json.JSONObject(src.readUTF());
    this.job = src.readBoolean() ? src.readLong() : null;
  }

  public static AsyncRequestReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "AsyncRequest");
    return new AsyncRequestReq(
      obj.getJSONObject("src")
    , obj.has("job") ? (obj.getLong("job")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("src", src);
    if (job != null) _ret.put("job", job);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeUTF(src.toString());
    if (job != null) { dst.writeBoolean(true); dst.writeLong(job);; } else { dst.writeBoolean(false); }
  }
}
