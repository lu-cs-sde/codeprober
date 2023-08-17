package codeprober.protocol.data;

import org.json.JSONObject;

public class BackingFile implements codeprober.util.JsonUtil.ToJsonable {
  public final String path;
  public final String value;
  public BackingFile(String path, String value) {
    this.path = path;
    this.value = value;
  }

  public static BackingFile fromJSON(JSONObject obj) {
    return new BackingFile(
      obj.getString("path")
    , obj.getString("value")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("path", path);
    _ret.put("value", value);
    return _ret;
  }
}
