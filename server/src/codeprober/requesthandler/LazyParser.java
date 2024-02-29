package codeprober.requesthandler;

import java.util.List;

import codeprober.AstInfo;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.RpcBodyLine;

public interface LazyParser {
	ParsedAst parse(String inputText, AstCacheStrategy optCacheStrategyVal, List<String> optArgsOverrideVal,
			PositionRecoveryStrategy posRecovery, String tmpFileSuffix);

	void discardCachedAst();

	default ParsedAst parse(ParsingRequestData prd) {
		return parse(prd.text, prd.cache, prd.mainArgs, prd.posRecovery, prd.tmpSuffix);
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
}
