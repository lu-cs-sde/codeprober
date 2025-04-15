package codeprober.protocol.data;

import org.json.JSONObject;

public class WsPutLongpollRes implements codeprober.util.JsonUtil.ToJsonable {
  public final LongPollResponse data;
  public WsPutLongpollRes() {
    this((LongPollResponse)null);
  }
  public WsPutLongpollRes(LongPollResponse data) {
    this.data = data;
  }
  public WsPutLongpollRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public WsPutLongpollRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.data = src.readBoolean() ? new LongPollResponse(src) : null;
  }

  public static WsPutLongpollRes fromJSON(JSONObject obj) {
    return new WsPutLongpollRes(
      obj.has("data") ? (LongPollResponse.fromJSON(obj.getJSONObject("data"))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (data != null) _ret.put("data", data.toJSON());
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    if (data != null) { dst.writeBoolean(true); data.writeTo(dst);; } else { dst.writeBoolean(false); }
  }
}
