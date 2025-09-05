package codeprober.textprobe;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TextAssertionMatch extends TextQueryMatch {
	public final boolean exclamation;
	public final boolean tilde;
	public final String expectVal;

	public TextAssertionMatch(String full, int lineIdx, String nodeType, Integer nodeIndex, String[] attrNames,
			boolean exclamation, boolean tilde, String expectVal) {
		super(full, lineIdx, nodeType, nodeIndex, attrNames);
		this.exclamation = exclamation;
		this.tilde = tilde;
		this.expectVal = expectVal;
	}

	@Override
	public String toString() {
		if (expectVal == null) {
			return super.toString();
		}
		return Arrays.asList( //
				super.toString(), exclamation ? "!" : "", //
				tilde ? "~" : "", "=" + expectVal //
		).stream().collect(Collectors.joining(""));
	}
}