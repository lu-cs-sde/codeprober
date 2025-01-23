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
  public ListedTreeNode(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public ListedTreeNode(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "node";
    this.locator = new NodeLocator(src);
    this.name = src.readBoolean() ? src.readUTF() : null;
    this.children = new ListedTreeChildNode(src);
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
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    locator.writeTo(dst);
    if (name != null) { dst.writeBoolean(true); dst.writeUTF(name);; } else { dst.writeBoolean(false); }
    children.writeTo(dst);
  }
}
