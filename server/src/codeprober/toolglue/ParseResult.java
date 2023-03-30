package codeprober.toolglue;

import org.json.JSONArray;

public class ParseResult {
	public final Object rootNode;
	public final JSONArray captures;

	public ParseResult(Object rootNode) {
		this(rootNode, null);
	}

	public ParseResult(Object rootNode, JSONArray captures) {
		this.rootNode = rootNode;
		this.captures = captures != null ? captures : new JSONArray();
	}
}