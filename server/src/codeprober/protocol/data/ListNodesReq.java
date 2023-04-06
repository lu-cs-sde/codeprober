package codeprober.protocol.data;

import org.json.JSONObject;

public class ListNodesReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final int pos;
  public final ParsingRequestData src;
  public ListNodesReq(int pos, ParsingRequestData src) {
    this.type = "ListNodes";
    this.pos = pos;
    this.src = src;
  }

  public static ListNodesReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "ListNodes");
    return new ListNodesReq(
      obj.getInt("pos")
    , ParsingRequestData.fromJSON(obj.getJSONObject("src"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("pos", pos);
    _ret.put("src", src.toJSON());
    return _ret;
  }
}
