package codeprober.protocol.data;

import org.json.JSONObject;

public class HoverReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final ParsingRequestData src;
  public final int line;
  public final int column;
  public HoverReq(ParsingRequestData src, int line, int column) {
    this.type = "ide:hover";
    this.src = src;
    this.line = line;
    this.column = column;
  }

  public static HoverReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "ide:hover");
    return new HoverReq(
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
}
