package codeprober.textprobe;

public class VarAssignMatch {
	public final String full;
	public final int lineIdx;
	public final int columnIdx;
	public final String varName;
	public final String srcVal;

	public VarAssignMatch(String full, int lineIdx, int columnIdx, String varName, String srcVal) {
		this.full = full;
		this.lineIdx = lineIdx;
		this.columnIdx = columnIdx;
		this.varName = varName;
		this.srcVal = srcVal;
	}

	public TextQueryMatch matchSrcAsQuery() {
		return TextProbeParser.matchTextQuery(srcVal, lineIdx, columnIdx);
	}

	@Override
	public String toString() {
		return String.format("%s:=%s", varName, srcVal);
	}
}