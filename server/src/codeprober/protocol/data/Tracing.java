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
}
