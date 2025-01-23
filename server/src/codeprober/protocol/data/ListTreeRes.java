package codeprober.protocol.data;

import org.json.JSONObject;

public class ListTreeRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<RpcBodyLine> body;
  public final NodeLocator locator;
  public final ListedTreeNode node;
  public ListTreeRes(java.util.List<RpcBodyLine> body, NodeLocator locator) {
    this(body, locator, (ListedTreeNode)null);
  }
  public ListTreeRes(java.util.List<RpcBodyLine> body) {
    this(body, (NodeLocator)null, (ListedTreeNode)null);
  }
  public ListTreeRes(java.util.List<RpcBodyLine> body, NodeLocator locator, ListedTreeNode node) {
    this.body = body;
    this.locator = locator;
    this.node = node;
  }
  public ListTreeRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public ListTreeRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.body = codeprober.util.JsonUtil.<RpcBodyLine>readDataArr(src, () -> new RpcBodyLine(src));
    this.locator = src.readBoolean() ? new NodeLocator(src) : null;
    this.node = src.readBoolean() ? new ListedTreeNode(src) : null;
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
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    codeprober.util.JsonUtil.<RpcBodyLine>writeDataArr(dst, body, ent -> ent.writeTo(dst));
    if (locator != null) { dst.writeBoolean(true); locator.writeTo(dst);; } else { dst.writeBoolean(false); }
    if (node != null) { dst.writeBoolean(true); node.writeTo(dst);; } else { dst.writeBoolean(false); }
  }
}
