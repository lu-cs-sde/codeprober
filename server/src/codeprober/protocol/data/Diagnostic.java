package codeprober.protocol.data;

import org.json.JSONObject;

public class Diagnostic implements codeprober.util.JsonUtil.ToJsonable {
  public final codeprober.protocol.DiagnosticType type;
  public final int start;
  public final int end;
  public final String msg;
  public Diagnostic(codeprober.protocol.DiagnosticType type, int start, int end, String msg) {
    this.type = type;
    this.start = start;
    this.end = end;
    this.msg = msg;
  }

  public static Diagnostic fromJSON(JSONObject obj) {
    return new Diagnostic(
      codeprober.protocol.DiagnosticType.parseFromJson(obj.getString("type"))
    , obj.getInt("start")
    , obj.getInt("end")
    , obj.getString("msg")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type.name());
    _ret.put("start", start);
    _ret.put("end", end);
    _ret.put("msg", msg);
    return _ret;
  }
}
