package codeprober.protocol.data;

import org.json.JSONObject;

public class PutTestSuiteRes implements codeprober.util.JsonUtil.ToJsonable {
  public final codeprober.protocol.PutTestSuiteContentsErrorCode err;
  public PutTestSuiteRes() {
    this((codeprober.protocol.PutTestSuiteContentsErrorCode)null);
  }
  public PutTestSuiteRes(codeprober.protocol.PutTestSuiteContentsErrorCode err) {
    this.err = err;
  }
  public PutTestSuiteRes(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public PutTestSuiteRes(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.err = src.readBoolean() ? codeprober.protocol.PutTestSuiteContentsErrorCode.values()[src.readInt()] : null;
  }

  public static PutTestSuiteRes fromJSON(JSONObject obj) {
    return new PutTestSuiteRes(
      obj.has("err") ? (codeprober.protocol.PutTestSuiteContentsErrorCode.parseFromJson(obj.getString("err"))) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    if (err != null) _ret.put("err", err.name());
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    if (err != null) { dst.writeBoolean(true); dst.writeInt(err.ordinal());; } else { dst.writeBoolean(false); }
  }
}
