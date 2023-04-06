package codeprober.protocol.data;

import org.json.JSONObject;

public class NodeLocator implements codeprober.util.JsonUtil.ToJsonable {
  public final TALStep result;
  public final java.util.List<NodeLocatorStep> steps;
  public NodeLocator(TALStep result, java.util.List<NodeLocatorStep> steps) {
    this.result = result;
    this.steps = steps;
  }

  public static NodeLocator fromJSON(JSONObject obj) {
    return new NodeLocator(
      TALStep.fromJSON(obj.getJSONObject("result"))
    , codeprober.util.JsonUtil.<NodeLocatorStep>mapArr(obj.getJSONArray("steps"), (arr, idx) -> NodeLocatorStep.fromJSON(arr.getJSONObject(idx)))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("result", result.toJSON());
    _ret.put("steps", new org.json.JSONArray(steps.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
}
