package codeprober.requesthandler;

import codeprober.locator.ApplyLocator;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.locator.CreateLocator;
import codeprober.locator.CreateLocator.LocatorMergeMethod;
import codeprober.locator.DataDrivenListTree;
import codeprober.protocol.data.ListTreeReq;
import codeprober.protocol.data.ListTreeRes;
import codeprober.protocol.data.ListedTreeNode;
import codeprober.requesthandler.LazyParser.ParsedAst;

public class ListTreeRequestHandler {

	public static ListTreeRes apply(ListTreeReq req, LazyParser parser) {
		final ParsedAst parsed = parser.parse(req.src);
		if (parsed.info == null) {
			return new ListTreeRes(parsed.captures, null, null);
		}
		final ResolvedNode match = ApplyLocator.toNode(parsed.info, req.locator);
		if (match == null) {
			return new ListTreeRes(parsed.captures, null, null);
		}
		CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
		try {
			final ListedTreeNode listing;
			switch (req.type) {
			case "ListTreeUpwards": {
				listing = DataDrivenListTree.listUpwards(parsed.info, match.node);
				break;
			}
			case "ListTreeDownwards": {
				listing = DataDrivenListTree.listDownwards(parsed.info, match.node, 32);
				break;
			}
			default: {
				throw new Error("Should never happen - matched request with incorrect type");
			}
			}
			return new ListTreeRes(parsed.captures, match.nodeLocator, listing);
		} finally {
			CreateLocator.setMergeMethod(LocatorMergeMethod.DEFAULT_METHOD);
		}
	}
}
