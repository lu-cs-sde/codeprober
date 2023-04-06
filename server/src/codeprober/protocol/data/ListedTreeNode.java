package codeprober.protocol.data;

import org.json.JSONObject;

public class ListedTreeNode implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final NodeLocator locator;
  public final String name;
  public final ListedTreeChildNode children;
  public ListedTreeNode(NodeLocator locator, String name, ListedTreeChildNode children) {
    this.type = "node";
    this.locator = locator;
    this.name = name;
    this.children = children;
  }

  public static ListedTreeNode fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "node");
    return new ListedTreeNode(
      NodeLocator.fromJSON(obj.getJSONObject("locator"))
    , obj.has("name") ? (obj.getString("name")) : null
    , ListedTreeChildNode.fromJSON(obj.getJSONObject("children"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("locator", locator.toJSON());
    if (name != null) _ret.put("name", name);
    _ret.put("children", children.toJSON());
    return _ret;
  }
}
