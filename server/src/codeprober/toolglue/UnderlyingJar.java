package codeprober.toolglue;

import codeprober.util.ASTProvider;

public class UnderlyingJar implements UnderlyingTool {

	private final String jarPath;
	private long changeCounter = 0;

	public UnderlyingJar(String jarPath) {
		this.jarPath = jarPath;
	}

	@Override
	public long getVersionId() {
		if (!ASTProvider.hasUnchangedJar(jarPath)) {
			++changeCounter;
		}
		return changeCounter;
	}

	@Override
	public ParseResult parse(String[] args) {
		return ASTProvider.parseAst(jarPath, args);
	}

	@Override
	public String toString() {
		return String.format("%s:%s", getClass().getSimpleName(), jarPath);
	}
}
