package codeprober.protocol.data;

import org.json.JSONObject;

public class ListPropertiesReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final boolean all;
  public final NodeLocator locator;
  public final ParsingRequestData src;
  public ListPropertiesReq(boolean all, NodeLocator locator, ParsingRequestData src) {
    this.type = "ListProperties";
    this.all = all;
    this.locator = locator;
    this.src = src;
  }

  public static ListPropertiesReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "ListProperties");
    return new ListPropertiesReq(
      obj.getBoolean("all")
    , NodeLocator.fromJSON(obj.getJSONObject("locator"))
    , ParsingRequestData.fromJSON(obj.getJSONObject("src"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("all", all);
    _ret.put("locator", locator.toJSON());
    _ret.put("src", src.toJSON());
    return _ret;
  }
}
