package codeprober.protocol.data;

import org.json.JSONObject;

public class Tracing implements codeprober.util.JsonUtil.ToJsonable {
  public final NodeLocator node;
  public final Property prop;
  public final java.util.List<Tracing> dependencies;
  public final RpcBodyLine result;
  public final Boolean isCircular;
  public Tracing(NodeLocator node, Property prop, java.util.List<Tracing> dependencies, RpcBodyLine result) {
    this(node, prop, dependencies, result, (Boolean)null);
  }
  public Tracing(NodeLocator node, Property prop, java.util.List<Tracing> dependencies, RpcBodyLine result, Boolean isCircular) {
    this.node = node;
    this.prop = prop;
    this.dependencies = dependencies;
    this.result = result;
    this.isCircular = isCircular;
  }
  public Tracing(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public Tracing(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.node = new NodeLocator(src);
    this.prop = new Property(src);
    this.dependencies = codeprober.util.JsonUtil.<Tracing>readDataArr(src, () -> new Tracing(src));
    this.result = new RpcBodyLine(src);
    this.isCircular = src.readBoolean() ? src.readBoolean() : null;
  }

  public static Tracing fromJSON(JSONObject obj) {
    return new Tracing(
      NodeLocator.fromJSON(obj.getJSONObject("node"))
    , Property.fromJSON(obj.getJSONObject("prop"))
    , codeprober.util.JsonUtil.<Tracing>mapArr(obj.getJSONArray("dependencies"), (arr31, idx31) -> Tracing.fromJSON(arr31.getJSONObject(idx31)))
    , RpcBodyLine.fromJSON(obj.getJSONObject("result"))
    , obj.has("isCircular") ? (obj.getBoolean("isCircular")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("node", node.toJSON());
    _ret.put("prop", prop.toJSON());
    _ret.put("dependencies", new org.json.JSONArray(dependencies.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    _ret.put("result", result.toJSON());
    if (isCircular != null) _ret.put("isCircular", isCircular);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    node.writeTo(dst);
    prop.writeTo(dst);
    codeprober.util.JsonUtil.<Tracing>writeDataArr(dst, dependencies, ent31 -> ent31.writeTo(dst));
    result.writeTo(dst);
    if (isCircular != null) { dst.writeBoolean(true); dst.writeBoolean(isCircular);; } else { dst.writeBoolean(false); }
  }
}
