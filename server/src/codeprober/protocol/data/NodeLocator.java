package codeprober.protocol.data;

import org.json.JSONObject;

public class NodeLocator implements codeprober.util.JsonUtil.ToJsonable {
  public final TALStep result;
  public final java.util.List<NodeLocatorStep> steps;
  public NodeLocator(TALStep result, java.util.List<NodeLocatorStep> steps) {
    this.result = result;
    this.steps = steps;
  }
  public NodeLocator(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public NodeLocator(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.result = new TALStep(src);
    this.steps = codeprober.util.JsonUtil.<NodeLocatorStep>readDataArr(src, () -> new NodeLocatorStep(src));
  }

  public static NodeLocator fromJSON(JSONObject obj) {
    return new NodeLocator(
      TALStep.fromJSON(obj.getJSONObject("result"))
    , codeprober.util.JsonUtil.<NodeLocatorStep>mapArr(obj.getJSONArray("steps"), (arr16, idx16) -> NodeLocatorStep.fromJSON(arr16.getJSONObject(idx16)))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("result", result.toJSON());
    _ret.put("steps", new org.json.JSONArray(steps.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    result.writeTo(dst);
    codeprober.util.JsonUtil.<NodeLocatorStep>writeDataArr(dst, steps, ent16 -> ent16.writeTo(dst));
  }
}
