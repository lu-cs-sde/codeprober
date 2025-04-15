package codeprober.protocol.data;

import org.json.JSONObject;

public class GetWorkerStatusReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public GetWorkerStatusReq() {
    this.type = "Concurrent:GetWorkerStatus";
  }
  public GetWorkerStatusReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public GetWorkerStatusReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "Concurrent:GetWorkerStatus";
  }

  public static GetWorkerStatusReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Concurrent:GetWorkerStatus");
    return new GetWorkerStatusReq(
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
  }
}
