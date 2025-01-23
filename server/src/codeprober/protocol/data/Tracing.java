package codeprober.protocol.data;

import org.json.JSONObject;

public class Tracing implements codeprober.util.JsonUtil.ToJsonable {
  public final NodeLocator node;
  public final Property prop;
  public final java.util.List<Tracing> dependencies;
  public final RpcBodyLine result;
  public Tracing(NodeLocator node, Property prop, java.util.List<Tracing> dependencies, RpcBodyLine result) {
    this.node = node;
    this.prop = prop;
    this.dependencies = dependencies;
    this.result = result;
  }
  public Tracing(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public Tracing(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.node = new NodeLocator(src);
    this.prop = new Property(src);
    this.dependencies = codeprober.util.JsonUtil.<Tracing>readDataArr(src, () -> new Tracing(src));
    this.result = new RpcBodyLine(src);
  }

  public static Tracing fromJSON(JSONObject obj) {
    return new Tracing(
      NodeLocator.fromJSON(obj.getJSONObject("node"))
    , Property.fromJSON(obj.getJSONObject("prop"))
    , codeprober.util.JsonUtil.<Tracing>mapArr(obj.getJSONArray("dependencies"), (arr, idx) -> Tracing.fromJSON(arr.getJSONObject(idx)))
    , RpcBodyLine.fromJSON(obj.getJSONObject("result"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("node", node.toJSON());
    _ret.put("prop", prop.toJSON());
    _ret.put("dependencies", new org.json.JSONArray(dependencies.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    _ret.put("result", result.toJSON());
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    node.writeTo(dst);
    prop.writeTo(dst);
    codeprober.util.JsonUtil.<Tracing>writeDataArr(dst, dependencies, ent -> ent.writeTo(dst));
    result.writeTo(dst);
  }
}
