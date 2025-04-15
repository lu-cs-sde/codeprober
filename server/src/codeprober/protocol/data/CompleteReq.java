package codeprober.protocol.data;

import org.json.JSONObject;

public class CompleteReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final ParsingRequestData src;
  public final int line;
  public final int column;
  public CompleteReq(ParsingRequestData src, int line, int column) {
    this.type = "ide:complete";
    this.src = src;
    this.line = line;
    this.column = column;
  }
  public CompleteReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public CompleteReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "ide:complete";
    this.src = new ParsingRequestData(src);
    this.line = src.readInt();
    this.column = src.readInt();
  }

  public static CompleteReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "ide:complete");
    return new CompleteReq(
      ParsingRequestData.fromJSON(obj.getJSONObject("src"))
    , obj.getInt("line")
    , obj.getInt("column")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("src", src.toJSON());
    _ret.put("line", line);
    _ret.put("column", column);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    src.writeTo(dst);
    dst.writeInt(line);
    dst.writeInt(column);
  }
}
