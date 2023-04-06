package codeprober.protocol.data;

import org.json.JSONObject;

public class StopJobReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final int job;
  public StopJobReq(int job) {
    this.type = "Concurrent:StopJob";
    this.job = job;
  }

  public static StopJobReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "Concurrent:StopJob");
    return new StopJobReq(
      obj.getInt("job")
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("job", job);
    return _ret;
  }
}
