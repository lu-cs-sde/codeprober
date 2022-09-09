package codeprober.metaprogramming;

public enum AstNodeApiStyle {

	// getStart() and getEnd(), each with 20 bits for line and 12 bits for column, e.g:
	// 0xLLLLLCCC
	// getNumChild(), getChild(int), getParent()
	BEAVER_PACKED_BITS,

	// getStartLine(), getStartColumn(), getEndLine(), getEndColumn()
	// getNumChild(), getChild(int), getParent()
	JASTADD_SEPARATE_LINE_COLUMN,

	// The style used by PMD. getBeginLine(), getBeginColumn(), getEndLine(), getEndColumn()
	// getNumChildren(), getChild(int), getParent()
	PMD_SEPARATE_LINE_COLUMN,
}
