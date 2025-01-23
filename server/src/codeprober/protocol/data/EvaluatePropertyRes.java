package codeprober.protocol.data;

import org.json.JSONObject;

public class EvaluatePropertyRes implements codeprober.util.JsonUtil.ToJsonable {
  public final PropertyEvaluationResult response;
  public EvaluatePropertyRes(PropertyEvaluationResult response) {
    this.response = response;
  }
  public EvaluatePropertyRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public EvaluatePropertyRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.response = new PropertyEvaluationResult(src);
  }

  public static EvaluatePropertyRes fromJSON(JSONObject obj) {
    return new EvaluatePropertyRes(
      PropertyEvaluationResult.fromJSON(obj.getJSONObject("response"))
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
