package codeprober.protocol.data;

import org.json.JSONObject;

public class SynchronousEvaluationResult implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<RpcBodyLine> body;
  public final long totalTime;
  public final long parseTime;
  public final long createLocatorTime;
  public final long applyLocatorTime;
  public final long attrEvalTime;
  public final long listNodesTime;
  public final long listPropertiesTime;
  public final java.util.List<Diagnostic> errors;
  public final java.util.List<PropertyArg> args;
  public final NodeLocator locator;
  public SynchronousEvaluationResult(java.util.List<RpcBodyLine> body, long totalTime, long parseTime, long createLocatorTime, long applyLocatorTime, long attrEvalTime, long listNodesTime, long listPropertiesTime, java.util.List<Diagnostic> errors, java.util.List<PropertyArg> args) {
    this(body, totalTime, parseTime, createLocatorTime, applyLocatorTime, attrEvalTime, listNodesTime, listPropertiesTime, errors, args, null);
  }
  public SynchronousEvaluationResult(java.util.List<RpcBodyLine> body, long totalTime, long parseTime, long createLocatorTime, long applyLocatorTime, long attrEvalTime, long listNodesTime, long listPropertiesTime, java.util.List<Diagnostic> errors) {
    this(body, totalTime, parseTime, createLocatorTime, applyLocatorTime, attrEvalTime, listNodesTime, listPropertiesTime, errors, null, null);
  }
  public SynchronousEvaluationResult(java.util.List<RpcBodyLine> body, long totalTime, long parseTime, long createLocatorTime, long applyLocatorTime, long attrEvalTime, long listNodesTime, long listPropertiesTime) {
    this(body, totalTime, parseTime, createLocatorTime, applyLocatorTime, attrEvalTime, listNodesTime, listPropertiesTime, null, null, null);
  }
  public SynchronousEvaluationResult(java.util.List<RpcBodyLine> body, long totalTime, long parseTime, long createLocatorTime, long applyLocatorTime, long attrEvalTime, long listNodesTime, long listPropertiesTime, java.util.List<Diagnostic> errors, java.util.List<PropertyArg> args, NodeLocator locator) {
    this.body = body;
    this.totalTime = totalTime;
    this.parseTime = parseTime;
    this.createLocatorTime = createLocatorTime;
    this.applyLocatorTime = applyLocatorTime;
    this.attrEvalTime = attrEvalTime;
    this.listNodesTime = listNodesTime;
    this.listPropertiesTime = listPropertiesTime;
    this.errors = errors;
    this.args = args;
    this.locator = locator;
  }

  public static SynchronousEvaluationResult fromJSON(JSONObject obj) {
    return new SynchronousEvaluationResult(
      codeprober.util.JsonUtil.<RpcBodyLine>mapArr(obj.getJSONArray("body"), (arr, idx) -> RpcBodyLine.fromJSON(arr.getJSONObject(idx)))
    , obj.getLong("totalTime")
    , obj.getLong("parseTime")
    , obj.getLong("createLocatorTime")
    , obj.getLong("applyLocatorTime")
    , obj.getLong("attrEvalTime")
    , obj.getLong("listNodesTime")
    , obj.getLong("listPropertiesTime")
    , obj.has("errors") ? (codeprober.util.JsonUtil.<Diagnostic>mapArr(obj.getJSONArray("errors"), (arr, idx) -> Diagnostic.fromJSON(arr.getJSONObject(idx)))) : null
    , obj.has("args") ? (codeprober.util.JsonUtil.<PropertyArg>mapArr(obj.getJSONArray("args"), (arr, idx) -> PropertyArg.fromJSON(arr.getJSONObject(idx)))) : null
    , obj.has("locator") ? (NodeLocator.fromJSON(obj.getJSONObject("locator"))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("body", new org.json.JSONArray(body.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    _ret.put("totalTime", totalTime);
    _ret.put("parseTime", parseTime);
    _ret.put("createLocatorTime", createLocatorTime);
    _ret.put("applyLocatorTime", applyLocatorTime);
    _ret.put("attrEvalTime", attrEvalTime);
    _ret.put("listNodesTime", listNodesTime);
    _ret.put("listPropertiesTime", listPropertiesTime);
    if (errors != null) _ret.put("errors", new org.json.JSONArray(errors.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    if (args != null) _ret.put("args", new org.json.JSONArray(args.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    if (locator != null) _ret.put("locator", locator.toJSON());
    return _ret;
  }
}
