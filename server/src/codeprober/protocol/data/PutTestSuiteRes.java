package codeprober.protocol.data;

import org.json.JSONObject;

public class PutTestSuiteRes implements codeprober.util.JsonUtil.ToJsonable {
  public final codeprober.protocol.PutTestSuiteContentsErrorCode err;
  public PutTestSuiteRes(codeprober.protocol.PutTestSuiteContentsErrorCode err) {
    this.err = err;
  }

  public static PutTestSuiteRes fromJSON(JSONObject obj) {
    return new PutTestSuiteRes(
      obj.has("err") ? (codeprober.protocol.PutTestSuiteContentsErrorCode.parseFromJson(obj.getString("err"))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (err != null) _ret.put("err", err.name());
    return _ret;
  }
}
