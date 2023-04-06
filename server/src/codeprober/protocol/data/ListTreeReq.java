package codeprober.protocol.data;

import org.json.JSONObject;

public class ListTreeReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final NodeLocator locator;
  public final ParsingRequestData src;
  public ListTreeReq(String type, NodeLocator locator, ParsingRequestData src) {
    this.type = type;
    this.locator = locator;
    this.src = src;
  }

  public static ListTreeReq fromJSON(JSONObject obj) {
    return new ListTreeReq(
      codeprober.util.JsonUtil.requireString(obj.getString("type"), "ListTreeUpwards", "ListTreeDownwards")
    , NodeLocator.fromJSON(obj.getJSONObject("locator"))
    , ParsingRequestData.fromJSON(obj.getJSONObject("src"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("locator", locator.toJSON());
    _ret.put("src", src.toJSON());
    return _ret;
  }
}
