package codeprober.toolglue;

/**
 * A representation of the "underlying tool" that parses files and constructs an
 * AST that CodeProber later will traverse and invoke attributes on.
 * <p>
 * Usually this is represented by a jar file. However, when running tests, this
 * will be some in-memory handler.
 */
public interface UnderlyingTool {

	/**
	 * Get an opaque identifier for the "version" of the tool. This is important for
	 * caching purposes. Parsed ASTs may be reused whenever a request is received
	 * with identical sources as before, and when the underlying tool is unchanged.
	 * <p>
	 * Default implementation assumes a short-lived tool that doesn't change during.
	 * Therefore the version is by default hard-coded to 0.
	 */
	default long getVersionId() {
		return 0;
	}

	ParseResult parse(String[] args);

	public static UnderlyingTool fromJar(String jarPath) {
		UnderlyingTool ret = new UnderlyingJar(jarPath);
		if ("true".equals(System.getProperty("cpr.resetASTStateOnParse", "true"))) {
			ret = new AstStateResetter(ret);
		}
		return ret;
	}
}
