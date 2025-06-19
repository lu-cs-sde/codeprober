package codeprober.protocol.data;

import org.json.JSONObject;

public class NodeContainer implements codeprober.util.JsonUtil.ToJsonable {
  public final NodeLocator node;
  public final RpcBodyLine body;
  public NodeContainer(NodeLocator node) {
    this(node, (RpcBodyLine)null);
  }
  public NodeContainer(NodeLocator node, RpcBodyLine body) {
    this.node = node;
    this.body = body;
  }
  public NodeContainer(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public NodeContainer(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.node = new NodeLocator(src);
    this.body = src.readBoolean() ? new RpcBodyLine(src) : null;
  }

  public static NodeContainer fromJSON(JSONObject obj) {
    return new NodeContainer(
      NodeLocator.fromJSON(obj.getJSONObject("node"))
    , obj.has("body") ? (RpcBodyLine.fromJSON(obj.getJSONObject("body"))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("node", node.toJSON());
    if (body != null) _ret.put("body", body.toJSON());
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    node.writeTo(dst);
    if (body != null) { dst.writeBoolean(true); body.writeTo(dst);; } else { dst.writeBoolean(false); }
  }
}
