package codeprober.protocol.data;

import org.json.JSONObject;

public class ListedTreeNode implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final NodeLocator locator;
  public final String name;
  public final ListedTreeChildNode children;
  public final java.util.List<NodeLocator> remotes;
  public ListedTreeNode(NodeLocator locator, String name, ListedTreeChildNode children) {
    this(locator, name, children, (java.util.List<NodeLocator>)null);
  }
  public ListedTreeNode(NodeLocator locator, String name, ListedTreeChildNode children, java.util.List<NodeLocator> remotes) {
    this.type = "node";
    this.locator = locator;
    this.name = name;
    this.children = children;
    this.remotes = remotes;
  }
  public ListedTreeNode(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public ListedTreeNode(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "node";
    this.locator = new NodeLocator(src);
    this.name = src.readBoolean() ? src.readUTF() : null;
    this.children = new ListedTreeChildNode(src);
    this.remotes = src.readBoolean() ? codeprober.util.JsonUtil.<NodeLocator>readDataArr(src, () -> new NodeLocator(src)) : null;
  }

  public static ListedTreeNode fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "node");
    return new ListedTreeNode(
      NodeLocator.fromJSON(obj.getJSONObject("locator"))
    , obj.has("name") ? (obj.getString("name")) : null
    , ListedTreeChildNode.fromJSON(obj.getJSONObject("children"))
    , obj.has("remotes") ? (codeprober.util.JsonUtil.<NodeLocator>mapArr(obj.getJSONArray("remotes"), (arr1, idx1) -> NodeLocator.fromJSON(arr1.getJSONObject(idx1)))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("locator", locator.toJSON());
    if (name != null) _ret.put("name", name);
    _ret.put("children", children.toJSON());
    if (remotes != null) _ret.put("remotes", new org.json.JSONArray(remotes.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    locator.writeTo(dst);
    if (name != null) { dst.writeBoolean(true); dst.writeUTF(name);; } else { dst.writeBoolean(false); }
    children.writeTo(dst);
    if (remotes != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<NodeLocator>writeDataArr(dst, remotes, ent1 -> ent1.writeTo(dst));; } else { dst.writeBoolean(false); }
  }
}
