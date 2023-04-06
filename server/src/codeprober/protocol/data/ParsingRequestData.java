package codeprober.protocol.data;

import org.json.JSONObject;

public class ParsingRequestData implements codeprober.util.JsonUtil.ToJsonable {
  public final codeprober.protocol.PositionRecoveryStrategy posRecovery;
  public final codeprober.protocol.AstCacheStrategy cache;
  public final String text;
  public final java.util.List<String> mainArgs;
  public final String tmpSuffix;
  public ParsingRequestData(codeprober.protocol.PositionRecoveryStrategy posRecovery, codeprober.protocol.AstCacheStrategy cache, String text, java.util.List<String> mainArgs, String tmpSuffix) {
    this.posRecovery = posRecovery;
    this.cache = cache;
    this.text = text;
    this.mainArgs = mainArgs;
    this.tmpSuffix = tmpSuffix;
  }

  public static ParsingRequestData fromJSON(JSONObject obj) {
    return new ParsingRequestData(
      codeprober.protocol.PositionRecoveryStrategy.parseFromJson(obj.getString("posRecovery"))
    , codeprober.protocol.AstCacheStrategy.parseFromJson(obj.getString("cache"))
    , obj.getString("text")
    , obj.has("mainArgs") ? (codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("mainArgs"), (arr, idx) -> arr.getString(idx))) : null
    , obj.getString("tmpSuffix")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("posRecovery", posRecovery.name());
    _ret.put("cache", cache.name());
    _ret.put("text", text);
    _ret.put("mainArgs", new org.json.JSONArray(mainArgs));
    _ret.put("tmpSuffix", tmpSuffix);
    return _ret;
  }
}
