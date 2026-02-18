package codeprober.protocol.data;

import org.json.JSONObject;

public class GetDecorationsReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final ParsingRequestData src;
  public final Boolean forceAllOK;
  public GetDecorationsReq(ParsingRequestData src) {
    this(src, (Boolean)null);
  }
  public GetDecorationsReq(ParsingRequestData src, Boolean forceAllOK) {
    this.type = "ide:decorations";
    this.src = src;
    this.forceAllOK = forceAllOK;
  }
  public GetDecorationsReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public GetDecorationsReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "ide:decorations";
    this.src = new ParsingRequestData(src);
    this.forceAllOK = src.readBoolean() ? src.readBoolean() : null;
  }

  public static GetDecorationsReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "ide:decorations");
    return new GetDecorationsReq(
      ParsingRequestData.fromJSON(obj.getJSONObject("src"))
    , obj.has("forceAllOK") ? (obj.getBoolean("forceAllOK")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("src", src.toJSON());
    if (forceAllOK != null) _ret.put("forceAllOK", forceAllOK);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    src.writeTo(dst);
    if (forceAllOK != null) { dst.writeBoolean(true); dst.writeBoolean(forceAllOK);; } else { dst.writeBoolean(false); }
  }
}
