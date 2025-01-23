package codeprober.protocol.data;

import org.json.JSONObject;

public class TunneledWsPutRequestRes implements codeprober.util.JsonUtil.ToJsonable {
  public final org.json.JSONObject response;
  public TunneledWsPutRequestRes(org.json.JSONObject response) {
    this.response = response;
  }
  public TunneledWsPutRequestRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public TunneledWsPutRequestRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.response = new org.json.JSONObject(src.readUTF());
  }

  public static TunneledWsPutRequestRes fromJSON(JSONObject obj) {
    return new TunneledWsPutRequestRes(
      obj.getJSONObject("response")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("response", response);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeUTF(response.toString());
  }
}
