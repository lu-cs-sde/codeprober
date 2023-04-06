package codeprober.protocol.data;

import org.json.JSONObject;

public class ListNodesRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<RpcBodyLine> body;
  public final java.util.List<NodeLocator> nodes;
  public ListNodesRes(java.util.List<RpcBodyLine> body, java.util.List<NodeLocator> nodes) {
    this.body = body;
    this.nodes = nodes;
  }

  public static ListNodesRes fromJSON(JSONObject obj) {
    return new ListNodesRes(
      codeprober.util.JsonUtil.<RpcBodyLine>mapArr(obj.getJSONArray("body"), (arr, idx) -> RpcBodyLine.fromJSON(arr.getJSONObject(idx)))
    , obj.has("nodes") ? (codeprober.util.JsonUtil.<NodeLocator>mapArr(obj.getJSONArray("nodes"), (arr, idx) -> NodeLocator.fromJSON(arr.getJSONObject(idx)))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("body", new org.json.JSONArray(body.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    if (nodes != null) _ret.put("nodes", new org.json.JSONArray(nodes.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
}
