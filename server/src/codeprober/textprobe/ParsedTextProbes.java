package codeprober.textprobe;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class ParsedTextProbes {

	public final List<VarAssignMatch> assignments = new ArrayList<>();
	public final List<TextAssertionMatch> assertions = new ArrayList<>();

	public static ParsedTextProbes fromFileContents(String src) {
		final ParsedTextProbes ret = new ParsedTextProbes();

		final String[] lines = src.split("\n");
		for (int lineIdx = 0; lineIdx < lines.length; ++lineIdx) {
			final String line = lines[lineIdx];
			final Matcher containerMatcher = TextProbeParser.getProbeContainerMatcher().matcher(line);
			while (containerMatcher.find()) {
				final String containerContents = containerMatcher.group(TextProbeParser.CONTAINER_GROUP_CONTENT);
				final int colIdx = containerMatcher.start() + 2; // +2 for '[['

				final VarAssignMatch varMatch = TextProbeParser.matchVarAssign(containerContents, lineIdx, colIdx);
				if (varMatch != null) {
					ret.assignments.add(varMatch);
				} else {
					final TextAssertionMatch tp = TextProbeParser.matchTextAssertion(containerContents, lineIdx, colIdx);
					if (tp != null) {
						ret.assertions.add(tp);
					} else {
						System.out.println("container < " + containerContents +" > is not a textProbe");
					}
				}
			}
		}

		return ret;
	}

}
