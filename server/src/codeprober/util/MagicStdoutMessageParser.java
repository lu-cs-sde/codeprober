package codeprober.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import codeprober.protocol.DiagnosticType;
import codeprober.protocol.data.Diagnostic;

public class MagicStdoutMessageParser {

	public static Diagnostic parse(String line) {
		final Matcher matcher = Pattern.compile("(ERR|WARN|INFO|HINT|LINE-PP|LINE-AA|LINE-AP|LINE-PA)@(\\d+);(\\d+);(.*)")
				.matcher(line);
		if (!matcher.matches()) {
			return null;
		}
		final int start = Integer.parseInt(matcher.group(2));
		final int end = Integer.parseInt(matcher.group(3));
		final String msg = matcher.group(4);
		final DiagnosticType type;
		switch (matcher.group(1)) {
		case "ERR":
			type = DiagnosticType.ERROR;
			break;
		case "WARN":
			type = DiagnosticType.WARNING;
			break;
		case "INFO":
			type = DiagnosticType.INFO;
			break;
		case "HINT":
			type = DiagnosticType.HINT;
			break;
		case "LINE-PP":
			type = DiagnosticType.LINE_PP;
			break;
		case "LINE-AA":
			type = DiagnosticType.LINE_AA;
			break;
		case "LINE-AP":
			type = DiagnosticType.LINE_AP;
			break;
		case "LINE-PA":
			type = DiagnosticType.LINE_PA;
			break;
		default:
			type = DiagnosticType.ERROR;
			break;
		}
		return new Diagnostic(type, start, end, msg);
	}
}
