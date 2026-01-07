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
    this(body, totalTime, parseTime, createLocatorTime, applyLocatorTime, attrEvalTime, listNodesTime, listPropertiesTime, errors, args, (NodeLocator)null);
  }
  public SynchronousEvaluationResult(java.util.List<RpcBodyLine> body, long totalTime, long parseTime, long createLocatorTime, long applyLocatorTime, long attrEvalTime, long listNodesTime, long listPropertiesTime, java.util.List<Diagnostic> errors) {
    this(body, totalTime, parseTime, createLocatorTime, applyLocatorTime, attrEvalTime, listNodesTime, listPropertiesTime, errors, (java.util.List<PropertyArg>)null, (NodeLocator)null);
  }
  public SynchronousEvaluationResult(java.util.List<RpcBodyLine> body, long totalTime, long parseTime, long createLocatorTime, long applyLocatorTime, long attrEvalTime, long listNodesTime, long listPropertiesTime) {
    this(body, totalTime, parseTime, createLocatorTime, applyLocatorTime, attrEvalTime, listNodesTime, listPropertiesTime, (java.util.List<Diagnostic>)null, (java.util.List<PropertyArg>)null, (NodeLocator)null);
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
  public SynchronousEvaluationResult(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public SynchronousEvaluationResult(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.body = codeprober.util.JsonUtil.<RpcBodyLine>readDataArr(src, () -> new RpcBodyLine(src));
    this.totalTime = src.readLong();
    this.parseTime = src.readLong();
    this.createLocatorTime = src.readLong();
    this.applyLocatorTime = src.readLong();
    this.attrEvalTime = src.readLong();
    this.listNodesTime = src.readLong();
    this.listPropertiesTime = src.readLong();
    this.errors = src.readBoolean() ? codeprober.util.JsonUtil.<Diagnostic>readDataArr(src, () -> new Diagnostic(src)) : null;
    this.args = src.readBoolean() ? codeprober.util.JsonUtil.<PropertyArg>readDataArr(src, () -> new PropertyArg(src)) : null;
    this.locator = src.readBoolean() ? new NodeLocator(src) : null;
  }

  public static SynchronousEvaluationResult fromJSON(JSONObject obj) {
    return new SynchronousEvaluationResult(
      codeprober.util.JsonUtil.<RpcBodyLine>mapArr(obj.getJSONArray("body"), (arr28, idx28) -> RpcBodyLine.fromJSON(arr28.getJSONObject(idx28)))
    , obj.getLong("totalTime")
    , obj.getLong("parseTime")
    , obj.getLong("createLocatorTime")
    , obj.getLong("applyLocatorTime")
    , obj.getLong("attrEvalTime")
    , obj.getLong("listNodesTime")
    , obj.getLong("listPropertiesTime")
    , obj.has("errors") ? (codeprober.util.JsonUtil.<Diagnostic>mapArr(obj.getJSONArray("errors"), (arr29, idx29) -> Diagnostic.fromJSON(arr29.getJSONObject(idx29)))) : null
    , obj.has("args") ? (codeprober.util.JsonUtil.<PropertyArg>mapArr(obj.getJSONArray("args"), (arr30, idx30) -> PropertyArg.fromJSON(arr30.getJSONObject(idx30)))) : null
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
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    codeprober.util.JsonUtil.<RpcBodyLine>writeDataArr(dst, body, ent28 -> ent28.writeTo(dst));
    dst.writeLong(totalTime);
    dst.writeLong(parseTime);
    dst.writeLong(createLocatorTime);
    dst.writeLong(applyLocatorTime);
    dst.writeLong(attrEvalTime);
    dst.writeLong(listNodesTime);
    dst.writeLong(listPropertiesTime);
    if (errors != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<Diagnostic>writeDataArr(dst, errors, ent29 -> ent29.writeTo(dst));; } else { dst.writeBoolean(false); }
    if (args != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<PropertyArg>writeDataArr(dst, args, ent30 -> ent30.writeTo(dst));; } else { dst.writeBoolean(false); }
    if (locator != null) { dst.writeBoolean(true); locator.writeTo(dst);; } else { dst.writeBoolean(false); }
  }
}
