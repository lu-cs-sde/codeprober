package codeprober.protocol.data;

import org.json.JSONObject;

public class ListPropertiesReq implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final boolean all;
  public final NodeLocator locator;
  public final ParsingRequestData src;
  public final java.util.List<String> attrChain;
  public ListPropertiesReq(boolean all, NodeLocator locator, ParsingRequestData src) {
    this(all, locator, src, (java.util.List<String>)null);
  }
  public ListPropertiesReq(boolean all, NodeLocator locator, ParsingRequestData src, java.util.List<String> attrChain) {
    this.type = "ListProperties";
    this.all = all;
    this.locator = locator;
    this.src = src;
    this.attrChain = attrChain;
  }
  public ListPropertiesReq(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public ListPropertiesReq(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "ListProperties";
    this.all = src.readBoolean();
    this.locator = new NodeLocator(src);
    this.src = new ParsingRequestData(src);
    this.attrChain = src.readBoolean() ? codeprober.util.JsonUtil.<String>readDataArr(src, () -> src.readUTF()) : null;
  }

  public static ListPropertiesReq fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "ListProperties");
    return new ListPropertiesReq(
      obj.getBoolean("all")
    , NodeLocator.fromJSON(obj.getJSONObject("locator"))
    , ParsingRequestData.fromJSON(obj.getJSONObject("src"))
    , obj.has("attrChain") ? (codeprober.util.JsonUtil.<String>mapArr(obj.getJSONArray("attrChain"), (arr4, idx4) -> arr4.getString(idx4))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("all", all);
    _ret.put("locator", locator.toJSON());
    _ret.put("src", src.toJSON());
    if (attrChain != null) _ret.put("attrChain", new org.json.JSONArray(attrChain));
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    dst.writeBoolean(all);
    locator.writeTo(dst);
    src.writeTo(dst);
    if (attrChain != null) { dst.writeBoolean(true); codeprober.util.JsonUtil.<String>writeDataArr(dst, attrChain, ent4 -> dst.writeUTF(ent4));; } else { dst.writeBoolean(false); }
  }
}
