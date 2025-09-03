package codeprober.requesthandler;

import java.util.List;

import codeprober.AstInfo;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.data.GetWorkspaceFileReq;
import codeprober.protocol.data.GetWorkspaceFileRes;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.RpcBodyLine;

public interface LazyParser {
	ParsedAst parse(ParsingSource inputText, AstCacheStrategy optCacheStrategyVal, List<String> optArgsOverrideVal,
			PositionRecoveryStrategy posRecovery, String tmpFileSuffix);

	void discardCachedAst();

	default ParsedAst parse(ParsingRequestData prd) {
		return parse(prd.src, prd.cache, prd.mainArgs, prd.posRecovery, prd.tmpSuffix);
	}

	static class ParsedAst {
		public final AstInfo info;
		public final long parseTimeNanos;
		public final List<RpcBodyLine> captures;

		public ParsedAst(AstInfo info) {
			this(info, 0L, null);
		}

		public ParsedAst(AstInfo info, long parseTimeNanos, List<RpcBodyLine> captures) {
			this.info = info;
			this.parseTimeNanos = parseTimeNanos;
			this.captures = captures;
		}
	}

	public static String extractText(ParsingSource src) {
		return extractText(src, WorkspaceHandler.getDefault());
	}

	public static String extractText(ParsingSource src, WorkspaceHandler wsHandler) {
		switch (src.type) {
		case text: {
			return src.asText();
		}
		case workspacePath: {
			final GetWorkspaceFileRes res = wsHandler
					.handleGetWorkspaceFile(new GetWorkspaceFileReq(src.asWorkspacePath()));
			if (res.content == null) {
				System.err.println("Tried parsing non-existing workspace path '" + src.asWorkspacePath() + "'");
			}
			return res.content;
		}
		default: {
			System.err.println("Unknown source type: " + src.type);
			return null;
		}
		}
	}
}
