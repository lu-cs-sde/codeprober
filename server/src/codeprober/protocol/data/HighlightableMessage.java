package codeprober.protocol.data;

import org.json.JSONObject;

public class HighlightableMessage implements codeprober.util.JsonUtil.ToJsonable {
  public final int start;
  public final int end;
  public final String msg;
  public HighlightableMessage(int start, int end, String msg) {
    this.start = start;
    this.end = end;
    this.msg = msg;
  }

  public static HighlightableMessage fromJSON(JSONObject obj) {
    return new HighlightableMessage(
      obj.getInt("start")
    , obj.getInt("end")
    , obj.getString("msg")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("start", start);
    _ret.put("end", end);
    _ret.put("msg", msg);
    return _ret;
  }
}
