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
  public final Boolean flattenForTextProbes;
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout, Long job, String jobLabel, Boolean skipResultLocator, Boolean captureTraces, Boolean flushBeforeTraceCollection) {
    this(src, locator, property, captureStdout, job, jobLabel, skipResultLocator, captureTraces, flushBeforeTraceCollection, (Boolean)null);
  }
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout, Long job, String jobLabel, Boolean skipResultLocator, Boolean captureTraces) {
    this(src, locator, property, captureStdout, job, jobLabel, skipResultLocator, captureTraces, (Boolean)null, (Boolean)null);
  }
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout, Long job, String jobLabel, Boolean skipResultLocator) {
    this(src, locator, property, captureStdout, job, jobLabel, skipResultLocator, (Boolean)null, (Boolean)null, (Boolean)null);
  }
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout, Long job, String jobLabel) {
    this(src, locator, property, captureStdout, job, jobLabel, (Boolean)null, (Boolean)null, (Boolean)null, (Boolean)null);
  }
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout, Long job) {
    this(src, locator, property, captureStdout, job, (String)null, (Boolean)null, (Boolean)null, (Boolean)null, (Boolean)null);
  }
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout) {
    this(src, locator, property, captureStdout, (Long)null, (String)null, (Boolean)null, (Boolean)null, (Boolean)null, (Boolean)null);
  }
  public EvaluatePropertyReq(ParsingRequestData src, NodeLocator locator, Property property, boolean captureStdout, Long job, String jobLabel, Boolean skipResultLocator, Boolean captureTraces, Boolean flushBeforeTraceCollection, Boolean flattenForTextProbes) {
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
    this.flattenForTextProbes = flattenForTextProbes;
  }
  public EvaluatePropertyReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public EvaluatePropertyReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "EvaluateProperty";
    this.src = new ParsingRequestData(src);
    this.locator = new NodeLocator(src);
    this.property = new Property(src);
    this.captureStdout = src.readBoolean();
    this.job = src.readBoolean() ? src.readLong() : null;
    this.jobLabel = src.readBoolean() ? src.readUTF() : null;
    this.skipResultLocator = src.readBoolean() ? src.readBoolean() : null;
    this.captureTraces = src.readBoolean() ? src.readBoolean() : null;
    this.flushBeforeTraceCollection = src.readBoolean() ? src.readBoolean() : null;
    this.flattenForTextProbes = src.readBoolean() ? src.readBoolean() : null;
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
    , obj.has("flattenForTextProbes") ? (obj.getBoolean("flattenForTextProbes")) : null
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
    if (flattenForTextProbes != null) _ret.put("flattenForTextProbes", flattenForTextProbes);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    src.writeTo(dst);
    locator.writeTo(dst);
    property.writeTo(dst);
    dst.writeBoolean(captureStdout);
    if (job != null) { dst.writeBoolean(true); dst.writeLong(job);; } else { dst.writeBoolean(false); }
    if (jobLabel != null) { dst.writeBoolean(true); dst.writeUTF(jobLabel);; } else { dst.writeBoolean(false); }
    if (skipResultLocator != null) { dst.writeBoolean(true); dst.writeBoolean(skipResultLocator);; } else { dst.writeBoolean(false); }
    if (captureTraces != null) { dst.writeBoolean(true); dst.writeBoolean(captureTraces);; } else { dst.writeBoolean(false); }
    if (flushBeforeTraceCollection != null) { dst.writeBoolean(true); dst.writeBoolean(flushBeforeTraceCollection);; } else { dst.writeBoolean(false); }
    if (flattenForTextProbes != null) { dst.writeBoolean(true); dst.writeBoolean(flattenForTextProbes);; } else { dst.writeBoolean(false); }
  }
}
