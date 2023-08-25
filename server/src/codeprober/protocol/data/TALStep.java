package codeprober.protocol.data;

import org.json.JSONObject;

public class TALStep implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final String label;
  public final int start;
  public final int end;
  public final int depth;
  public final Boolean external;
  public TALStep(String type, String label, int start, int end, int depth) {
    this(type, label, start, end, depth, null);
  }
  public TALStep(String type, String label, int start, int end, int depth, Boolean external) {
    this.type = type;
    this.label = label;
    this.start = start;
    this.end = end;
    this.depth = depth;
    this.external = external;
  }

  public static TALStep fromJSON(JSONObject obj) {
    return new TALStep(
      obj.getString("type")
    , obj.has("label") ? (obj.getString("label")) : null
    , obj.getInt("start")
    , obj.getInt("end")
    , obj.getInt("depth")
    , obj.has("external") ? (obj.getBoolean("external")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    if (label != null) _ret.put("label", label);
    _ret.put("start", start);
    _ret.put("end", end);
    _ret.put("depth", depth);
    if (external != null) _ret.put("external", external);
    return _ret;
  }
}
