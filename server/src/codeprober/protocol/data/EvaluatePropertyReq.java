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
  public final Boolean captureTraces;
  public final Boolean flushBeforeTraceCollection;
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout, Long job, String jobLabel, Boolean skipResultLocator, Boolean captureTraces) {
    this(src, locator, property, captureStdout, job, jobLabel, skipResultLocator, captureTraces, null);
  }
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout, Long job, String jobLabel, Boolean skipResultLocator) {
    this(src, locator, property, captureStdout, job, jobLabel, skipResultLocator, null, null);
  }
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout, Long job, String jobLabel) {
    this(src, locator, property, captureStdout, job, jobLabel, null, null, null);
  }
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout, Long job) {
    this(src, locator, property, captureStdout, job, null, null, null, null);
  }
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout) {
    this(src, locator, property, captureStdout, null, null, null, null, null);
  }
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout, Long job, String jobLabel, Boolean skipResultLocator, Boolean captureTraces, Boolean flushBeforeTraceCollection) {
    this.type = "EvaluateProperty";
    this.src = src;
    this.locator = locator;
    this.property = property;
    this.captureStdout = captureStdout;
    this.job = job;
    this.jobLabel = jobLabel;
    this.skipResultLocator = skipResultLocator;
    this.captureTraces = captureTraces;
    this.flushBeforeTraceCollection = flushBeforeTraceCollection;
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
    , obj.has("captureTraces") ? (obj.getBoolean("captureTraces")) : null
    , obj.has("flushBeforeTraceCollection") ? (obj.getBoolean("flushBeforeTraceCollection")) : null
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
    if (captureTraces != null) _ret.put("captureTraces", captureTraces);
    if (flushBeforeTraceCollection != null) _ret.put("flushBeforeTraceCollection", flushBeforeTraceCollection);
    return _ret;
  }
}
