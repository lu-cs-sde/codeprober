package codeprober.protocol.data;

import org.json.JSONObject;

public class TopRequestReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final long id;
  public final org.json.JSONObject data;
  public TopRequestReq(long id, org.json.JSONObject data) {
    this.type = "rpc";
    this.id = id;
    this.data = data;
  }
  public TopRequestReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public TopRequestReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "rpc";
    this.id = src.readLong();
    this.data = new org.json.JSONObject(src.readUTF());
  }

  public static TopRequestReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "rpc");
    return new TopRequestReq(
      obj.getLong("id")
    , obj.getJSONObject("data")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("id", id);
    _ret.put("data", data);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeLong(id);
    dst.writeUTF(data.toString());
  }
}
