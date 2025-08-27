package codeprober.metaprogramming;

public enum AstNodeApiStyle {

	/**
	 * Like {@link #CPR_SEPARATE_LINE_COLUMN}, but also includes the "cpr_" prefix
	 * for getChild and getNumChild. With this you can present a different AST
	 * hierarchy than is "actually" persent in your AST. Note that this can likely
	 * create node locator construction problems, unless done correctly. Therefore
	 * this style of API is undocumented and not recommended.
	 */
	CPR_EVERYTHING,

	/**
	 * cpr_getStartLine(), cpr_getStartColumn(), cpr_getEndLine(),
	 * cpr_getEndColumn(), getNumChild(), getChild(int), getParent()
	 * <p>
	 * This is same as {@link #JASTADD_SEPARATE_LINE_COLUMN} but with a cpr_ prefix
	 * for position info. This is to help tools that are using position for
	 * something else, and that don't necessarily use the same conventions as
	 * CodeProber. For example, CodeProber expects the first column to be 1, and a
	 * missing position to be zero. Other tools might already be implemented with a
	 * different standard, and can then use the cpr_ prefix to support both
	 * conventions simultaneously.
	 */
	CPR_SEPARATE_LINE_COLUMN,

	/**
	 * getStart() and getEnd(), each with 20 bits for line and 12 bits for column,
	 * e.g: 0xLLLLLCCC.
	 * <p>
	 * getNumChild(), getChild(int), getParent()
	 */
	BEAVER_PACKED_BITS,

	/**
	 * getStartLine(), getStartColumn(), getEndLine(), getEndColumn(),
	 * getNumChild(), getChild(int), getParent()
	 */
	JASTADD_SEPARATE_LINE_COLUMN,

	/**
	 * The style used by PMD. getBeginLine(), getBeginColumn(), getEndLine(),
	 * getEndColumn() getNumChildren(), getChild(int), getParent()
	 */
	PMD_SEPARATE_LINE_COLUMN,

	/**
	 * No line/column functions. getNumChild(), getChild(int), getParent()
	 */
	JASTADD_NO_POSITION,
}
