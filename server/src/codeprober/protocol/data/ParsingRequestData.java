package codeprober.protocol.data;

import org.json.JSONObject;

public class ParsingRequestData implements codeprober.util.JsonUtil.ToJsonable {
  public final codeprober.protocol.PositionRecoveryStrategy posRecovery;
  public final codeprober.protocol.AstCacheStrategy cache;
  public final ParsingSource src;
  public final java.util.List<String> mainArgs;
  public final String tmpSuffix;
  public ParsingRequestData(codeprober.protocol.PositionRecoveryStrategy posRecovery, codeprober.protocol.AstCacheStrategy cache, ParsingSource src, java.util.List<String> mainArgs, String tmpSuffix) {
    this.posRecovery = posRecovery;
    this.cache = cache;
    this.src = src;
    this.mainArgs = mainArgs;
    this.tmpSuffix = tmpSuffix;
  }

  public static ParsingRequestData fromJSON(JSONObject obj) {
    return new ParsingRequestData(
      codeprober.protocol.PositionRecoveryStrategy.parseFromJson(obj.getString("posRecovery"))
    , codeprober.protocol.AstCacheStrategy.parseFromJson(obj.getString("cache"))
    , ParsingSource.fromJSON(obj.getJSONObject("src"))
    , obj.has("mainArgs") ? (codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("mainArgs"), (arr, idx) -> arr.getString(idx))) : null
    , obj.getString("tmpSuffix")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("posRecovery", posRecovery.name());
    _ret.put("cache", cache.name());
    _ret.put("src", src.toJSON());
    _ret.put("mainArgs", new org.json.JSONArray(mainArgs));
    _ret.put("tmpSuffix", tmpSuffix);
    return _ret;
  }
}
