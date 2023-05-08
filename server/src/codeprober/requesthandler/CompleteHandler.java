package codeprober.requesthandler;

import codeprober.protocol.data.CompleteReq;
import codeprober.protocol.data.CompleteRes;
import codeprober.requesthandler.LazyParser.ParsedAst;

public class CompleteHandler {

	public static CompleteRes apply(CompleteReq req, LazyParser parser) {
		final ParsedAst parsed = parser.parse(req.src);
		return new CompleteRes(HoverHandler.extract0(parsed, "cpr_ide_complete", req.line, req.column));
	}

}
