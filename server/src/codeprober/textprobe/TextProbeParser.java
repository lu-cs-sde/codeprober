package codeprober.textprobe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextProbeParser {

	public static Pattern getVarAssignPattern() {
		return Pattern.compile("(\\$\\w+)(?::=((.)*))");
	}

	public static int VAR_PATTERN_GROUP_VARNAME = 1;
	public static int VAR_PATTERN_GROUP_SRCVAL = 2;

	public static VarAssignMatch matchVarAssign(String src, int lineIdx) {
		final Matcher matcher = getVarAssignPattern().matcher(src);
		if (!matcher.matches()) {
			return null;
		}

		final String nodeType = matcher.group(VAR_PATTERN_GROUP_VARNAME);
		final String srcVal = matcher.group(VAR_PATTERN_GROUP_SRCVAL);
		return new VarAssignMatch(matcher.group(0), lineIdx, nodeType, srcVal);
	}

	public static Pattern getProbeContainerMatcher() {
		return Pattern.compile("\\[\\[(?:(((?!\\[\\[).)*))\\]\\](?!\\])");
	}

	public static int CONTAINER_GROUP_CONTENT = 1;

	private static final String textQueryPattern = "((?:\\$)?\\w+)(\\[\\d+\\])?((?:\\.(?:l:)?\\w+)*)";

	public static Pattern getTextQueryPattern() {
		return Pattern.compile(textQueryPattern);
	}

	public static Pattern getTextAssertionPattern() {
		return Pattern.compile(textQueryPattern + "(!?)(~?)(?:=(.*))");
	}

	public static TextQueryMatch matchTextQuery(String src, int lineIdx) {
		final Matcher matcher = getTextQueryPattern().matcher(src);
		if (!matcher.matches()) {
			return null;
		}

		final String nodeType = matcher.group(PROBE_PATTERN_GROUP_NODETYPE);
		final String nodeIndex = matcher.group(PROBE_PATTERN_GROUP_NODEINDEX);
		final String rawAttrNames = matcher.group(PROBE_PATTERN_GROUP_ATTRNAMES);

		final String[] attrNames = rawAttrNames.isEmpty() ? new String[0] : rawAttrNames.substring(1).split("\\.");
		return new TextQueryMatch(matcher.group(0), lineIdx, nodeType,
				nodeIndex == null ? null : Integer.parseInt(nodeIndex.substring(1, nodeIndex.length() - 1)), attrNames);
	}

	public static int PROBE_PATTERN_GROUP_NODETYPE = 1;
	public static int PROBE_PATTERN_GROUP_NODEINDEX = 2;
	public static int PROBE_PATTERN_GROUP_ATTRNAMES = 3;
	public static int PROBE_PATTERN_GROUP_EXCLAMATION = 4;
	public static int PROBE_PATTERN_GROUP_TILDE = 5;
	public static int PROBE_PATTERN_GROUP_EXPECTVAL = 6;

	public static TextAssertionMatch matchTextAssertion(String src, int lineIdx) {
		final Matcher matcher = getTextAssertionPattern().matcher(src);
		if (!matcher.matches()) {
			final Matcher justQuery = Pattern.compile(textQueryPattern).matcher(src);
			if (justQuery.matches()) {
				final String nodeType = justQuery.group(PROBE_PATTERN_GROUP_NODETYPE);
				final String nodeIndex = justQuery.group(PROBE_PATTERN_GROUP_NODEINDEX);
				final String rawAttrNames = justQuery.group(PROBE_PATTERN_GROUP_ATTRNAMES);
				final String[] attrNames = rawAttrNames.isEmpty() ? new String[0] : rawAttrNames.substring(1).split("\\.");
				return new TextAssertionMatch(justQuery.group(0), lineIdx, nodeType,
						nodeIndex == null ? null : Integer.parseInt(nodeIndex.substring(1, nodeIndex.length() - 1)), attrNames,
						false, false, null);
			}
			return null;
		}

		final String nodeType = matcher.group(PROBE_PATTERN_GROUP_NODETYPE);
		final String nodeIndex = matcher.group(PROBE_PATTERN_GROUP_NODEINDEX);
		final String rawAttrNames = matcher.group(PROBE_PATTERN_GROUP_ATTRNAMES);
		final boolean exclamation = "!".equals(matcher.group(PROBE_PATTERN_GROUP_EXCLAMATION));
		final boolean tilde = "~".equals(matcher.group(PROBE_PATTERN_GROUP_TILDE));
		final String expectVal = matcher.group(PROBE_PATTERN_GROUP_EXPECTVAL);

		final String[] attrNames = rawAttrNames.isEmpty() ? new String[0] : rawAttrNames.substring(1).split("\\.");
		return new TextAssertionMatch(matcher.group(0), lineIdx, nodeType,
				nodeIndex == null ? null : Integer.parseInt(nodeIndex.substring(1, nodeIndex.length() - 1)), attrNames,
				exclamation, tilde, expectVal);
	}
}
