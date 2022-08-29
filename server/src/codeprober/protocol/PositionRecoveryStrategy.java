package codeprober.protocol;

/**
 * Some AST nodes lack position information. This should usually be fixed by
 * restructuring the parser, but can be solved "automatically" to some degree.
 * <p>
 * This enum lists some strategies for how to find/recover position information
 * when an AST node is missing it.
 */
public enum PositionRecoveryStrategy {

	/**
	 * Don't try to recover the position, be content with [0,0].
	 */
	FAIL,

	/**
	 * Search recursively upwards through parent nodes. If no position is found,
	 * then search downwards through child(0). Same as {@link #PARENT}, followed by
	 * {@link #CHILD} iff {@link #PARENT} failed.
	 */
	SEQUENCE_PARENT_CHILD,

	/**
	 * Search recursively downwards through child(0). If no position is found, then
	 * search upwards through parents. Same as {@link #CHILD}, followed by
	 * {@link #PARENT} iff {@link #CHILD} failed.
	 */
	SEQUENCE_CHILD_PARENT,

	/**
	 * Search for position information recursively upwards through parents.
	 */
	PARENT,

	/**
	 * Search for position information recursively downwards through child(0).
	 */
	CHILD,

	/**
	 * Zigzag up and down until position information is found. Assume going left is
	 * the same as following 'getParent', and going right is 'child(0)' in this
	 * "AST":
	 * 
	 * <pre>
	 * 	Root--Foo--Bar--Baz
	 * </pre>
	 * 
	 * If we use {@link #ALTERNATE_PARENT_CHILD} on 'Bar', then we will search the
	 * following in order:
	 * <ol>
	 * <li>Foo
	 * <li>Baz
	 * <li>Root
	 * </ol>
	 */
	ALTERNATE_PARENT_CHILD;

	public static PositionRecoveryStrategy fallbackParse(String paramValue) {
		try {
			return PositionRecoveryStrategy.valueOf(paramValue);
		} catch (IllegalArgumentException e) {
			System.out.println("Got invalid position recovery argument: " + paramValue);
			return FAIL;
		}
	}
}
