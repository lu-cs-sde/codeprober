package codeprober.protocol.data;

import org.json.JSONObject;

public class ListTreeReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final NodeLocator locator;
  public final ParsingRequestData src;
  public ListTreeReq(String type, NodeLocator locator, ParsingRequestData src) {
    this.type = type;
    this.locator = locator;
    this.src = src;
  }
  public ListTreeReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public ListTreeReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = codeprober.util.JsonUtil.requireString(src.readUTF());
    this.locator = new NodeLocator(src);
    this.src = new ParsingRequestData(src);
  }

  public static ListTreeReq fromJSON(JSONObject obj) {
    return new ListTreeReq(
      codeprober.util.JsonUtil.requireString(obj.getString("type"), "ListTreeUpwards", "ListTreeDownwards")
    , NodeLocator.fromJSON(obj.getJSONObject("locator"))
    , ParsingRequestData.fromJSON(obj.getJSONObject("src"))
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("locator", locator.toJSON());
    _ret.put("src", src.toJSON());
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeUTF(type);
    locator.writeTo(dst);
    src.writeTo(dst);
  }
}
