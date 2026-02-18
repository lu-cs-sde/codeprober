package codeprober.protocol.data;

import org.json.JSONObject;

public class AsyncRequestRes implements codeprober.util.JsonUtil.ToJsonable {
  public final AsyncResult response;
  public AsyncRequestRes(AsyncResult response) {
    this.response = response;
  }
  public AsyncRequestRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public AsyncRequestRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.response = new AsyncResult(src);
  }

  public static AsyncRequestRes fromJSON(JSONObject obj) {
    return new AsyncRequestRes(
      AsyncResult.fromJSON(obj.getJSONObject("response"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("response", response.toJSON());
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    response.writeTo(dst);
  }
}
