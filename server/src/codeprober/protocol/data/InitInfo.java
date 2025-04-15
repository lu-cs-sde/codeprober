package codeprober.protocol.data;

import org.json.JSONObject;

public class InitInfo implements codeprober.util.JsonUtil.ToJsonable {
  public final String type;
  public final protocolgen_spec_InitInfo_1 version;
  public final Integer changeBufferTime;
  public final Integer workerProcessCount;
  public final Boolean disableVersionCheckerByDefault;
  public final BackingFile backingFile;
  public InitInfo(protocolgen_spec_InitInfo_1 version, Integer changeBufferTime, Integer workerProcessCount, Boolean disableVersionCheckerByDefault) {
    this(version, changeBufferTime, workerProcessCount, disableVersionCheckerByDefault, (BackingFile)null);
  }
  public InitInfo(protocolgen_spec_InitInfo_1 version, Integer changeBufferTime, Integer workerProcessCount) {
    this(version, changeBufferTime, workerProcessCount, (Boolean)null, (BackingFile)null);
  }
  public InitInfo(protocolgen_spec_InitInfo_1 version, Integer changeBufferTime) {
    this(version, changeBufferTime, (Integer)null, (Boolean)null, (BackingFile)null);
  }
  public InitInfo(protocolgen_spec_InitInfo_1 version) {
    this(version, (Integer)null, (Integer)null, (Boolean)null, (BackingFile)null);
  }
  public InitInfo(protocolgen_spec_InitInfo_1 version, Integer changeBufferTime, Integer workerProcessCount, Boolean disableVersionCheckerByDefault, BackingFile backingFile) {
    this.type = "init";
    this.version = version;
    this.changeBufferTime = changeBufferTime;
    this.workerProcessCount = workerProcessCount;
    this.disableVersionCheckerByDefault = disableVersionCheckerByDefault;
    this.backingFile = backingFile;
  }
  public InitInfo(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public InitInfo(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.type = "init";
    this.version = new protocolgen_spec_InitInfo_1(src);
    this.changeBufferTime = src.readBoolean() ? src.readInt() : null;
    this.workerProcessCount = src.readBoolean() ? src.readInt() : null;
    this.disableVersionCheckerByDefault = src.readBoolean() ? src.readBoolean() : null;
    this.backingFile = src.readBoolean() ? new BackingFile(src) : null;
  }

  public static InitInfo fromJSON(JSONObject obj) {
    codeprober.util.JsonUtil.requireString(obj.getString("type"), "init");
    return new InitInfo(
      protocolgen_spec_InitInfo_1.fromJSON(obj.getJSONObject("version"))
    , obj.has("changeBufferTime") ? (obj.getInt("changeBufferTime")) : null
    , obj.has("workerProcessCount") ? (obj.getInt("workerProcessCount")) : null
    , obj.has("disableVersionCheckerByDefault") ? (obj.getBoolean("disableVersionCheckerByDefault")) : null
    , obj.has("backingFile") ? (BackingFile.fromJSON(obj.getJSONObject("backingFile"))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("type", type);
    _ret.put("version", version.toJSON());
    if (changeBufferTime != null) _ret.put("changeBufferTime", changeBufferTime);
    if (workerProcessCount != null) _ret.put("workerProcessCount", workerProcessCount);
    if (disableVersionCheckerByDefault != null) _ret.put("disableVersionCheckerByDefault", disableVersionCheckerByDefault);
    if (backingFile != null) _ret.put("backingFile", backingFile.toJSON());
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    
    version.writeTo(dst);
    if (changeBufferTime != null) { dst.writeBoolean(true); dst.writeInt(changeBufferTime);; } else { dst.writeBoolean(false); }
    if (workerProcessCount != null) { dst.writeBoolean(true); dst.writeInt(workerProcessCount);; } else { dst.writeBoolean(false); }
    if (disableVersionCheckerByDefault != null) { dst.writeBoolean(true); dst.writeBoolean(disableVersionCheckerByDefault);; } else { dst.writeBoolean(false); }
    if (backingFile != null) { dst.writeBoolean(true); backingFile.writeTo(dst);; } else { dst.writeBoolean(false); }
  }
}
