package codeprober.requesthandler;

import java.util.ArrayList;
import java.util.List;

import codeprober.locator.NodesAtPosition;
import codeprober.protocol.data.Diagnostic;
import codeprober.protocol.data.ListNodesReq;
import codeprober.protocol.data.ListNodesRes;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.requesthandler.LazyParser.ParsedAst;
import codeprober.util.MagicStdoutMessageParser;

public class ListNodesHandler {

	public static ListNodesRes apply(ListNodesReq req, LazyParser parser) {
		final ParsedAst parsed = parser.parse(req.src);
		if (parsed.info == null) {

			final List<Diagnostic> diagnostics = new ArrayList<>();
			for (RpcBodyLine line : parsed.captures) {
				switch (line.type) {
				case stdout: {
					final Diagnostic diagnostic = MagicStdoutMessageParser.parse(line.asStdout());
					if (diagnostic != null) {
						diagnostics.add(diagnostic);
					}
					break;
				}

				case stderr:
					final Diagnostic diagnostic = MagicStdoutMessageParser.parse(line.asStderr());
					if (diagnostic != null) {
						diagnostics.add(diagnostic);
					}
					break;

				default:
					break;
				}
			}

			return new ListNodesRes(parsed.captures, null, diagnostics);
		}
		return new ListNodesRes(parsed.captures, NodesAtPosition.get(parsed.info, parsed.info.ast, req.pos));
	}

}
