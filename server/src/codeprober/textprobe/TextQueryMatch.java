package codeprober.textprobe;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TextQueryMatch {
	public final String full;
	public final int lineIdx;
	public final int columnIdx;
	public final String nodeType;
	public final Integer nodeIndex;
	public final String[] attrNames;

	public TextQueryMatch(String full, int lineIdx, int columnIdx, String nodeType, Integer nodeIndex, String[] attrNames) {
		this.full = full;
		this.lineIdx = lineIdx;
		this.columnIdx = columnIdx;
		this.nodeType = nodeType;
		this.nodeIndex = nodeIndex;
		this.attrNames = attrNames;
	}

	@Override
	public String toString() {
		return Arrays.asList( //
				nodeType, //
				nodeIndex != null ? "[" + nodeIndex + "]" : "", //
				attrNames.length > 0 ? ("." + Arrays.asList(attrNames).stream().collect(Collectors.joining("."))) : "")
				.stream().collect(Collectors.joining());
	}
}