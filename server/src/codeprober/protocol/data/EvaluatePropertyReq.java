package codeprober.protocol.data;

import org.json.JSONObject;

public class EvaluatePropertyReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final ParsingRequestData src;
  public final NodeLocator locator;
  public final Property property;
  public final boolean captureStdout;
  public final Long job;
  public final String jobLabel;
  public final Boolean skipResultLocator;
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout, Long job, String jobLabel, Boolean skipResultLocator) {
    this.type = "EvaluateProperty";
    this.src = src;
    this.locator = locator;
    this.property = property;
    this.captureStdout = captureStdout;
    this.job = job;
    this.jobLabel = jobLabel;
    this.skipResultLocator = skipResultLocator;
  }

  public static EvaluatePropertyReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "EvaluateProperty");
    return new EvaluatePropertyReq(
      ParsingRequestData.fromJSON(obj.getJSONObject("src"))
    , NodeLocator.fromJSON(obj.getJSONObject("locator"))
    , Property.fromJSON(obj.getJSONObject("property"))
    , obj.getBoolean("captureStdout")
    , obj.has("job") ? (obj.getLong("job")) : null
    , obj.has("jobLabel") ? (obj.getString("jobLabel")) : null
    , obj.has("skipResultLocator") ? (obj.getBoolean("skipResultLocator")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("src", src.toJSON());
    _ret.put("locator", locator.toJSON());
    _ret.put("property", property.toJSON());
    _ret.put("captureStdout", captureStdout);
    if (job != null) _ret.put("job", job);
    if (jobLabel != null) _ret.put("jobLabel", jobLabel);
    if (skipResultLocator != null) _ret.put("skipResultLocator", skipResultLocator);
    return _ret;
  }
}
