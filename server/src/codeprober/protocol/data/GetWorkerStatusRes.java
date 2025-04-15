package codeprober.protocol.data;

import org.json.JSONObject;

public class GetWorkerStatusRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<String> stackTrace;
  public GetWorkerStatusRes(java.util.List<String> stackTrace) {
    this.stackTrace = stackTrace;
  }
  public GetWorkerStatusRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public GetWorkerStatusRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.stackTrace = codeprober.util.JsonUtil.<String>readDataArr(src, () -> src.readUTF());
  }

  public static GetWorkerStatusRes fromJSON(JSONObject obj) {
    return new GetWorkerStatusRes(
      codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("stackTrace"), (arr, idx) -> arr.getString(idx))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("stackTrace", new org.json.JSONArray(stackTrace));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    codeprober.util.JsonUtil.<String>writeDataArr(dst, stackTrace, ent -> dst.writeUTF(ent));
  }
}
