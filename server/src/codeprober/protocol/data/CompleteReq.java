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
}
