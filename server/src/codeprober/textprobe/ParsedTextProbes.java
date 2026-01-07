package codeprober.textprobe;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class ParsedTextProbes {

	public final String[] lines;
	public ParsedTextProbes(String[] lines) {
		this.lines = lines;
	}

	public final List<VarAssignMatch> assignments = new ArrayList<>();
	public final List<TextAssertionMatch> assertions = new ArrayList<>();

	public static ParsedTextProbes fromFileContents(String src) {
		final String[] lines = src.split("\n");
		final ParsedTextProbes ret = new ParsedTextProbes(lines);

		for (int lineIdx = 0; lineIdx < lines.length; ++lineIdx) {
			final String line = lines[lineIdx];
			parseLine(line, lineIdx, ret.assignments, ret.assertions);
		}

		return ret;
	}

	public static void parseLine(String line, int lineIdx, List<VarAssignMatch> assignments,
			List<TextAssertionMatch> assertions) {
		final Matcher containerMatcher = TextProbeParser.getProbeContainerMatcher().matcher(line);
		while (containerMatcher.find()) {
			final String containerContents = containerMatcher.group(TextProbeParser.CONTAINER_GROUP_CONTENT);
			final int colIdx = containerMatcher.start() + 2; // +2 for '[['

			final VarAssignMatch varMatch = TextProbeParser.matchVarAssign(containerContents, lineIdx, colIdx);
			if (varMatch != null) {
				assignments.add(varMatch);
			} else {
				final TextAssertionMatch tp = TextProbeParser.matchTextAssertion(containerContents, lineIdx, colIdx);
				if (tp != null) {
					assertions.add(tp);
				} else {
					System.out.println("container < " + containerContents + " > is not a textProbe");
				}
			}
		}
	}
}
