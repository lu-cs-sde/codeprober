package codeprober.protocol.data;

import org.json.JSONObject;

public class ListNodesRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<RpcBodyLine> body;
  public final java.util.List<NodeLocator> nodes;
  public final java.util.List<Diagnostic> errors;
  public ListNodesRes(java.util.List<RpcBodyLine> body, java.util.List<NodeLocator> nodes) {
    this(body, nodes, (java.util.List<Diagnostic>)null);
  }
  public ListNodesRes(java.util.List<RpcBodyLine> body) {
    this(body, (java.util.List<NodeLocator>)null, (java.util.List<Diagnostic>)null);
  }
  public ListNodesRes(java.util.List<RpcBodyLine> body, java.util.List<NodeLocator> nodes, java.util.List<Diagnostic> errors) {
    this.body = body;
    this.nodes = nodes;
    this.errors = errors;
  }
  public ListNodesRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public ListNodesRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.body = codeprober.util.JsonUtil.<RpcBodyLine>readDataArr(src, () -> new RpcBodyLine(src));
    this.nodes = src.readBoolean() ? codeprober.util.JsonUtil.<NodeLocator>readDataArr(src, () -> new NodeLocator(src)) : null;
    this.errors = src.readBoolean() ? codeprober.util.JsonUtil.<Diagnostic>readDataArr(src, () -> new Diagnostic(src)) : null;
  }

  public static ListNodesRes fromJSON(JSONObject obj) {
    return new ListNodesRes(
      codeprober.util.JsonUtil.<RpcBodyLine>mapArr(obj.getJSONArray("body"), (arr, idx) -> RpcBodyLine.fromJSON(arr.getJSONObject(idx)))
    , obj.has("nodes") ? (codeprober.util.JsonUtil.<NodeLocator>mapArr(obj.getJSONArray("nodes"), (arr, idx) -> NodeLocator.fromJSON(arr.getJSONObject(idx)))) : null
    , obj.has("errors") ? (codeprober.util.JsonUtil.<Diagnostic>mapArr(obj.getJSONArray("errors"), (arr, idx) -> Diagnostic.fromJSON(arr.getJSONObject(idx)))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("body", new org.json.JSONArray(body.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    if (nodes != null) _ret.put("nodes", new org.json.JSONArray(nodes.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    if (errors != null) _ret.put("errors", new org.json.JSONArray(errors.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    codeprober.util.JsonUtil.<RpcBodyLine>writeDataArr(dst, body, ent -> ent.writeTo(dst));
    if (nodes != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<NodeLocator>writeDataArr(dst, nodes, ent -> ent.writeTo(dst));; } else { dst.writeBoolean(false); }
    if (errors != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<Diagnostic>writeDataArr(dst, errors, ent -> ent.writeTo(dst));; } else { dst.writeBoolean(false); }
  }
}
