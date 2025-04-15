package codeprober.protocol.data;

import org.json.JSONObject;

public class FNStep implements codeprober.util.JsonUtil.ToJsonable {
  public final Property property;
  public FNStep(Property property) {
    this.property = property;
  }
  public FNStep(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public FNStep(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.property = new Property(src);
  }

  public static FNStep fromJSON(JSONObject obj) {
    return new FNStep(
      Property.fromJSON(obj.getJSONObject("property"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("property", property.toJSON());
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    property.writeTo(dst);
  }
}
