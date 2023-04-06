package codeprober.protocol.data;

import org.json.JSONObject;

public class protocolgen_spec_InitInfo_1 implements codeprober.util.JsonUtil.ToJsonable {
  public final String hash;
  public final boolean clean;
  public final Integer buildTimeSeconds;
  public protocolgen_spec_InitInfo_1(String hash, boolean clean, Integer buildTimeSeconds) {
    this.hash = hash;
    this.clean = clean;
    this.buildTimeSeconds = buildTimeSeconds;
  }

  public static protocolgen_spec_InitInfo_1 fromJSON(JSONObject obj) {
    return new protocolgen_spec_InitInfo_1(
      obj.getString("hash")
    , obj.getBoolean("clean")
    , obj.has("buildTimeSeconds") ? (obj.getInt("buildTimeSeconds")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("hash", hash);
    _ret.put("clean", clean);
    if (buildTimeSeconds != null) _ret.put("buildTimeSeconds", buildTimeSeconds);
    return _ret;
  }
}
