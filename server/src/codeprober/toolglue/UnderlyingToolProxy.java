package codeprober.toolglue;

import java.util.Arrays;

import codeprober.protocol.data.RpcBodyLine;

public class UnderlyingToolProxy implements UnderlyingTool {
	private UnderlyingTool target;

	public void setProxyTarget(UnderlyingTool target) {
		this.target = target;
	}

	@Override
	public long getVersionId() {
		final UnderlyingTool ut = target;
		return ut != null ? ut.getVersionId() : 0L;
	}

	@Override
	public ParseResult parse(String[] args) {
		final UnderlyingTool ut = target;
		return ut != null ? ut.parse(args)
				: new ParseResult(null,
						Arrays.asList(RpcBodyLine.fromStderr("Tool not specified yet, please upload yourtool.jar")));
	}

}
