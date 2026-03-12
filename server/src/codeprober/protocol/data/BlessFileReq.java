package codeprober.protocol.data;

import org.json.JSONObject;

public class BlessFileReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final ParsingRequestData src;
  public final codeprober.requesthandler.BlessFileMode mode;
  public BlessFileReq(ParsingRequestData src, codeprober.requesthandler.BlessFileMode mode) {
    this.type = "BlessFile";
    this.src = src;
    this.mode = mode;
  }
  public BlessFileReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public BlessFileReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "BlessFile";
    this.src = new ParsingRequestData(src);
    this.mode = codeprober.requesthandler.BlessFileMode.values()[src.readInt()];
  }

  public static BlessFileReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "BlessFile");
    return new BlessFileReq(
      ParsingRequestData.fromJSON(obj.getJSONObject("src"))
    , codeprober.requesthandler.BlessFileMode.parseFromJson(obj.getString("mode"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("src", src.toJSON());
    _ret.put("mode", mode.name());
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    src.writeTo(dst);
    dst.writeInt(mode.ordinal());
  }
}
