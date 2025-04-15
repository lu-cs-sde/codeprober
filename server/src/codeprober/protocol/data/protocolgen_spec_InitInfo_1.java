package codeprober.protocol.data;

import org.json.JSONObject;

public class protocolgen_spec_InitInfo_1 implements codeprober.util.JsonUtil.ToJsonable {
  public final String hash;
  public final boolean clean;
  public final Integer buildTimeSeconds;
  public protocolgen_spec_InitInfo_1(String hash, boolean clean) {
    this(hash, clean, (Integer)null);
  }
  public protocolgen_spec_InitInfo_1(String hash, boolean clean, Integer buildTimeSeconds) {
    this.hash = hash;
    this.clean = clean;
    this.buildTimeSeconds = buildTimeSeconds;
  }
  public protocolgen_spec_InitInfo_1(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public protocolgen_spec_InitInfo_1(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.hash = src.readUTF();
    this.clean = src.readBoolean();
    this.buildTimeSeconds = src.readBoolean() ? src.readInt() : null;
  }

  public static protocolgen_spec_InitInfo_1 fromJSON(JSONObject obj) {
    return new protocolgen_spec_InitInfo_1(
      obj.getString("hash")
    , obj.getBoolean("clean")
    , obj.has("buildTimeSeconds") ? (obj.getInt("buildTimeSeconds")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("hash", hash);
    _ret.put("clean", clean);
    if (buildTimeSeconds != null) _ret.put("buildTimeSeconds", buildTimeSeconds);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeUTF(hash);
    dst.writeBoolean(clean);
    if (buildTimeSeconds != null) { dst.writeBoolean(true); dst.writeInt(buildTimeSeconds);; } else { dst.writeBoolean(false); }
  }
}
