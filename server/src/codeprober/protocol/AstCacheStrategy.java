package codeprober.protocol;

/**
 * Caching the AST is great for performance, but can hurt the debugging process
 * sometimes. This enum represents the choices the user can make for caching.
 */
public enum AstCacheStrategy {

	/**
	 * Cache everything when possible.
	 */
	FULL,

	/**
	 * Keep the AST if the input file is identical, but call 'flushTreeCache' before
	 * evaluating any attribute.
	 * <p>
	 * This is a good default choice.
	 */
	PARTIAL,

	/**
	 * Don't cache the AST at all.
	 */
	NONE,

	/**
	 * Don't cache the AST or the underlying jar file. Really bad choice for
	 * performance!
	 */
	PURGE;

	public boolean canCacheAST() {
		switch (this) {
		case FULL:
		case PARTIAL:
			return true;

		default:
			return false;
		}
	}

	public static AstCacheStrategy fallbackParse(String paramValue) {
		try {
			return AstCacheStrategy.valueOf(paramValue);
		} catch (IllegalArgumentException e) {
			System.out.println("Got invalid position recovery argument: " + paramValue);
			return PARTIAL;
		}
	}

	public static AstCacheStrategy parseFromJson(String string) {
		return fallbackParse(string);
	}
}
