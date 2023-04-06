package codeprober.protocol.data;

import org.json.JSONObject;

public class ListTreeRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<RpcBodyLine> body;
  public final NodeLocator locator;
  public final ListedTreeNode node;
  public ListTreeRes(java.util.List<RpcBodyLine> body, NodeLocator locator, ListedTreeNode node) {
    this.body = body;
    this.locator = locator;
    this.node = node;
  }

  public static ListTreeRes fromJSON(JSONObject obj) {
    return new ListTreeRes(
      codeprober.util.JsonUtil.<RpcBodyLine>mapArr(obj.getJSONArray("body"), (arr, idx) -> RpcBodyLine.fromJSON(arr.getJSONObject(idx)))
    , obj.has("locator") ? (NodeLocator.fromJSON(obj.getJSONObject("locator"))) : null
    , obj.has("node") ? (ListedTreeNode.fromJSON(obj.getJSONObject("node"))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("body", new org.json.JSONArray(body.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    if (locator != null) _ret.put("locator", locator.toJSON());
    if (node != null) _ret.put("node", node.toJSON());
    return _ret;
  }
}
