package codeprober.requesthandler;

import codeprober.locator.ApplyLocator;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.locator.AttrsInNode;
import codeprober.protocol.data.ListPropertiesReq;
import codeprober.protocol.data.ListPropertiesRes;
import codeprober.requesthandler.LazyParser.ParsedAst;

public class ListPropertiesHandler {

	public static ListPropertiesRes apply(ListPropertiesReq req, LazyParser parser) {
		final ParsedAst parsed = parser.parse(req.src);
		if (parsed.info == null) {
			return new ListPropertiesRes(parsed.captures, null);
		}
		final ResolvedNode match = ApplyLocator.toNode(parsed.info, req.locator);

		return new ListPropertiesRes(parsed.captures, AttrsInNode.getTyped(parsed.info, match.node,
				AttrsInNode.extractFilter(parsed.info, match.node), req.all));
	}

}
