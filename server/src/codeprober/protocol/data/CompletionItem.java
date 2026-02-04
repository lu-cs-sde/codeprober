package codeprober.protocol.data;

import org.json.JSONObject;

public class CompletionItem implements codeprober.util.JsonUtil.ToJsonable {
  public final String label;
  public final String insertText;
  public final int kind;
  public final String sortText;
  public final String detail;
  public final Integer contextStart;
  public final Integer contextEnd;
  public final Integer insertStart;
  public final Integer insertEnd;
  public CompletionItem(String label, String insertText, int kind, String sortText, String detail, Integer contextStart, Integer contextEnd, Integer insertStart) {
    this(label, insertText, kind, sortText, detail, contextStart, contextEnd, insertStart, (Integer)null);
  }
  public CompletionItem(String label, String insertText, int kind, String sortText, String detail, Integer contextStart, Integer contextEnd) {
    this(label, insertText, kind, sortText, detail, contextStart, contextEnd, (Integer)null, (Integer)null);
  }
  public CompletionItem(String label, String insertText, int kind, String sortText, String detail, Integer contextStart) {
    this(label, insertText, kind, sortText, detail, contextStart, (Integer)null, (Integer)null, (Integer)null);
  }
  public CompletionItem(String label, String insertText, int kind, String sortText, String detail) {
    this(label, insertText, kind, sortText, detail, (Integer)null, (Integer)null, (Integer)null, (Integer)null);
  }
  public CompletionItem(String label, String insertText, int kind, String sortText) {
    this(label, insertText, kind, sortText, (String)null, (Integer)null, (Integer)null, (Integer)null, (Integer)null);
  }
  public CompletionItem(String label, String insertText, int kind) {
    this(label, insertText, kind, (String)null, (String)null, (Integer)null, (Integer)null, (Integer)null, (Integer)null);
  }
  public CompletionItem(String label, String insertText, int kind, String sortText, String detail, Integer contextStart, Integer contextEnd, Integer insertStart, Integer insertEnd) {
    this.label = label;
    this.insertText = insertText;
    this.kind = kind;
    this.sortText = sortText;
    this.detail = detail;
    this.contextStart = contextStart;
    this.contextEnd = contextEnd;
    this.insertStart = insertStart;
    this.insertEnd = insertEnd;
  }
  public CompletionItem(java.io.DataInputStream src) throws java.io.IOException {
    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));
  }
  public CompletionItem(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {
    this.label = src.readUTF();
    this.insertText = src.readUTF();
    this.kind = src.readInt();
    this.sortText = src.readBoolean() ? src.readUTF() : null;
    this.detail = src.readBoolean() ? src.readUTF() : null;
    this.contextStart = src.readBoolean() ? src.readInt() : null;
    this.contextEnd = src.readBoolean() ? src.readInt() : null;
    this.insertStart = src.readBoolean() ? src.readInt() : null;
    this.insertEnd = src.readBoolean() ? src.readInt() : null;
  }

  public static CompletionItem fromJSON(JSONObject obj) {
    return new CompletionItem(
      obj.getString("label")
    , obj.getString("insertText")
    , obj.getInt("kind")
    , obj.has("sortText") ? (obj.getString("sortText")) : null
    , obj.has("detail") ? (obj.getString("detail")) : null
    , obj.has("contextStart") ? (obj.getInt("contextStart")) : null
    , obj.has("contextEnd") ? (obj.getInt("contextEnd")) : null
    , obj.has("insertStart") ? (obj.getInt("insertStart")) : null
    , obj.has("insertEnd") ? (obj.getInt("insertEnd")) : null
    );
  }
  public JSONObject toJSON() {
    JSONObject _ret = new JSONObject();
    _ret.put("label", label);
    _ret.put("insertText", insertText);
    _ret.put("kind", kind);
    if (sortText != null) _ret.put("sortText", sortText);
    if (detail != null) _ret.put("detail", detail);
    if (contextStart != null) _ret.put("contextStart", contextStart);
    if (contextEnd != null) _ret.put("contextEnd", contextEnd);
    if (insertStart != null) _ret.put("insertStart", insertStart);
    if (insertEnd != null) _ret.put("insertEnd", insertEnd);
    return _ret;
  }
  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {
    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));
  }
  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {
    dst.writeUTF(label);
    dst.writeUTF(insertText);
    dst.writeInt(kind);
    if (sortText != null) { dst.writeBoolean(true); dst.writeUTF(sortText);; } else { dst.writeBoolean(false); }
    if (detail != null) { dst.writeBoolean(true); dst.writeUTF(detail);; } else { dst.writeBoolean(false); }
    if (contextStart != null) { dst.writeBoolean(true); dst.writeInt(contextStart);; } else { dst.writeBoolean(false); }
    if (contextEnd != null) { dst.writeBoolean(true); dst.writeInt(contextEnd);; } else { dst.writeBoolean(false); }
    if (insertStart != null) { dst.writeBoolean(true); dst.writeInt(insertStart);; } else { dst.writeBoolean(false); }
    if (insertEnd != null) { dst.writeBoolean(true); dst.writeInt(insertEnd);; } else { dst.writeBoolean(false); }
  }
}
