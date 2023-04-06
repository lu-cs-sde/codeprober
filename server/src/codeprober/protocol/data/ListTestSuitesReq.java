package codeprober.protocol.data;

import org.json.JSONObject;

public class ListTestSuitesReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public ListTestSuitesReq() {
    this.type = "Test:ListTestSuites";
  }

  public static ListTestSuitesReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Test:ListTestSuites");
    return new ListTestSuitesReq(
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    return _ret;
  }
}
