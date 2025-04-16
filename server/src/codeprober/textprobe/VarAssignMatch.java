package codeprober.textprobe;

public class VarAssignMatch {
	public final String full;
	public final int lineIdx;
	public final String varName;
	public final String srcVal;

	public VarAssignMatch(String full, int lineIdx, String varName, String srcVal) {
		this.full = full;
		this.lineIdx = lineIdx;
		this.varName = varName;
		this.srcVal = srcVal;
	}

	public TextQueryMatch matchSrcAsQuery() {
		return TextProbeParser.matchTextQuery(srcVal, lineIdx);
	}

	@Override
	public String toString() {
		return String.format("%s:=%s", varName, srcVal);
	}
}