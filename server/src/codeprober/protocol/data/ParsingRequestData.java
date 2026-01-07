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
  public ParsingRequestData(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public ParsingRequestData(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.posRecovery = codeprober.protocol.PositionRecoveryStrategy.values()[src.readInt()];
    this.cache = codeprober.protocol.AstCacheStrategy.values()[src.readInt()];
    this.src = new ParsingSource(src);
    this.mainArgs = src.readBoolean() ? codeprober.util.JsonUtil.<String>readDataArr(src, () -> src.readUTF()) : null;
    this.tmpSuffix = src.readUTF();
  }

  public static ParsingRequestData fromJSON(JSONObject obj) {
    return new ParsingRequestData(
      codeprober.protocol.PositionRecoveryStrategy.parseFromJson(obj.getString("posRecovery"))
    , codeprober.protocol.AstCacheStrategy.parseFromJson(obj.getString("cache"))
    , ParsingSource.fromJSON(obj.getJSONObject("src"))
    , obj.has("mainArgs") ? (codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("mainArgs"), (arr19, idx19) -> arr19.getString(idx19))) : null
    , obj.getString("tmpSuffix")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("posRecovery", posRecovery.name());
    _ret.put("cache", cache.name());
    _ret.put("src", src.toJSON());
    if (mainArgs != null) _ret.put("mainArgs", new org.json.JSONArray(mainArgs));
    _ret.put("tmpSuffix", tmpSuffix);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeInt(posRecovery.ordinal());
    dst.writeInt(cache.ordinal());
    src.writeTo(dst);
    if (mainArgs != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<String>writeDataArr(dst, mainArgs, ent19 -> dst.writeUTF(ent19));; } else { dst.writeBoolean(false); }
    dst.writeUTF(tmpSuffix);
  }
}
