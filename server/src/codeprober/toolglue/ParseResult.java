package codeprober.toolglue;

import java.util.Collections;
import java.util.List;

import codeprober.protocol.data.RpcBodyLine;

public class ParseResult {
	public final Object rootNode;
	public final List<RpcBodyLine> captures;

	public ParseResult(Object rootNode) {
		this(rootNode, null);
	}

	public ParseResult(Object rootNode, List<RpcBodyLine> captures) {
		this.rootNode = rootNode;
		this.captures = captures != null ? captures : Collections.emptyList();
	}
}