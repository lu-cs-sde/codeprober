package codeprober.protocol.data;

import org.json.JSONObject;

public class InitInfo implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final protocolgen_spec_InitInfo_1 version;
  public final Integer changeBufferTime;
  public final Integer workerProcessCount;
  public final Boolean disableVersionCheckerByDefault;
  public InitInfo(protocolgen_spec_InitInfo_1 version, Integer changeBufferTime, Integer workerProcessCount, Boolean disableVersionCheckerByDefault) {
    this.type = "init";
    this.version = version;
    this.changeBufferTime = changeBufferTime;
    this.workerProcessCount = workerProcessCount;
    this.disableVersionCheckerByDefault = disableVersionCheckerByDefault;
  }

  public static InitInfo fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "init");
    return new InitInfo(
      protocolgen_spec_InitInfo_1.fromJSON(obj.getJSONObject("version"))
    , obj.has("changeBufferTime") ? (obj.getInt("changeBufferTime")) : null
    , obj.has("workerProcessCount") ? (obj.getInt("workerProcessCount")) : null
    , obj.has("disableVersionCheckerByDefault") ? (obj.getBoolean("disableVersionCheckerByDefault")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("version", version.toJSON());
    if (changeBufferTime != null) _ret.put("changeBufferTime", changeBufferTime);
    if (workerProcessCount != null) _ret.put("workerProcessCount", workerProcessCount);
    if (disableVersionCheckerByDefault != null) _ret.put("disableVersionCheckerByDefault", disableVersionCheckerByDefault);
    return _ret;
  }
}
