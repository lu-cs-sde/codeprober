package codeprober.protocol.data;

import org.json.JSONObject;

public class ListPropertiesRes implements codeprober.util.JsonUtil.ToJsonable {
  public final java.util.List<RpcBodyLine> body;
  public final java.util.List<Property> properties;
  public ListPropertiesRes(java.util.List<RpcBodyLine> body, java.util.List<Property> properties) {
    this.body = body;
    this.properties = properties;
  }

  public static ListPropertiesRes fromJSON(JSONObject obj) {
    return new ListPropertiesRes(
      codeprober.util.JsonUtil.<RpcBodyLine>mapArr(obj.getJSONArray("body"), (arr, idx) -> RpcBodyLine.fromJSON(arr.getJSONObject(idx)))
    , obj.has("properties") ? (codeprober.util.JsonUtil.<Property>mapArr(obj.getJSONArray("properties"), (arr, idx) -> Property.fromJSON(arr.getJSONObject(idx)))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("body", new org.json.JSONArray(body.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    if (properties != null) _ret.put("properties", new org.json.JSONArray(properties.stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));
    return _ret;
  }
}
