package codeprober.requesthandler;

import codeprober.locator.NodesAtPosition;
import codeprober.protocol.data.ListNodesReq;
import codeprober.protocol.data.ListNodesRes;
import codeprober.requesthandler.LazyParser.ParsedAst;

public class ListNodesHandler {

	public static ListNodesRes apply(ListNodesReq req, LazyParser parser) {
		final ParsedAst parsed = parser.parse(req.src);
		if (parsed.info == null) {
			return new ListNodesRes(parsed.captures, null);
		}
		return new ListNodesRes(parsed.captures, NodesAtPosition.get(parsed.info, parsed.info.ast, req.pos));
	}

}
