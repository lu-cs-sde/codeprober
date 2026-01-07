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

	public TextQueryMatch(String full, int lineIdx, int columnIdx, String nodeType, Integer nodeIndex,
			String[] attrNames) {
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

	public int typeAndIndexLength() {
		if (nodeIndex == null) {
			return nodeType.length();
		}
		return nodeType.length() + nodeIndex.toString().length() + 2; // 2 = "[]".length
	}

	public Component componentAtColumn(int columnIdx) {
		final int localTc = columnIdx - this.columnIdx;
		System.out.println("Found TAM!");

		final int nodeTypeEnd = typeAndIndexLength();
		if (localTc <= nodeTypeEnd) {
			return new Component(ComponentType.NODE_TYPE, 0, nodeTypeEnd, -1);
		}
		int attrNameStart = nodeTypeEnd;
		if (attrNames.length > 0) {
			// Maybe completing an attr name?
			// ETC, todo like this. Move hover & complete logic from client to backend

			for (int attrIdx = 0; attrIdx < attrNames.length; ++attrIdx) {
				attrNameStart = full.indexOf(attrNames[attrIdx], attrNameStart + 1);
				final int attrNameEnd = attrNameStart + attrNames[attrIdx].length();
				System.out.println("Comparing localTC " + localTc + " w/ s/e: " + attrNameStart + "/" + attrNameEnd);

				if (localTc >= attrNameStart && localTc <= attrNameEnd) {
					return new Component(ComponentType.ATTR, attrNameStart, attrNameEnd, attrIdx);
				}
			}
		}
//		TODO for after vacation, why do all this manual parsing, and complex test cases (not written yet)?
//		Wouldn't it be make sense (and be super cool!) if the text probe was a proper language that was tested with text probes??

		// Maybe hovering complete?
//		final int expValStart = full.indexOf('=', attrNameStart) + 1;
//		final int expValEnd = expValStart + expectVal.length();
//		if (localTc >= expValStart && localTc <= expValStart + expectVal.length()) {
//			return new Component(ComponentType.EXPECTED_VALUE, expValStart);
//		}
		return null;
	}

	public enum ComponentType {
		NODE_TYPE, ATTR, EXPECTED_VALUE
	}

	public class Component {
		public final ComponentType type;
		public final int start;
		public final int end;
		public final int index;

		public Component(ComponentType type, int start, int end, int index) {
			this.type = type;
			this.start = start;
			this.end = end;
			this.index = index;
		}
	}
}