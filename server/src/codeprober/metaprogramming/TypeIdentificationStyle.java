package codeprober.metaprogramming;

import java.util.function.Function;

/**
 * Enum for different ways of comparing types. Type comparison is essential to
 * TAL (Type at Location) steps.
 */
public enum TypeIdentificationStyle {

	/**
	 * Default Java way of inspecting types. Uses foo.getClass(), Class.forName(..).
	 */
	REFLECTION,

	/**
	 * Use 'cpr_nodeLabel'. Suitable for tools that use node wrapper types, since
	 * every single AST node has the same type when using wrapper types.
	 * <p>
	 * Falls back to behaving like {@link #REFLECTION} when:
	 * <ol>
	 * <li>The TAL step being compared against doesn't have a label.</li>
	 * <li>The node begin compared against doesn't implement 'cpr_nodeLabel' with a
	 * non-null value.</li>
	 * </ol>
	 */
	NODE_LABEL;

	public static TypeIdentificationStyle parse(String val) {
		if (val != null) {
			try {
				return TypeIdentificationStyle.valueOf(val);
			} catch (IllegalArgumentException e) {
				System.out.println("Illegal " + TypeIdentificationStyle.class.getSimpleName() + " value '" + val + "'");
			}
		}
		return REFLECTION;
	}

	public TypeIdentifier createIdentifier(Function<String, Class<?>> loadAstClass, String searchType,
			String searchLabel) {
		final Class<?> clazz = loadAstClass.apply(searchType);
		switch (this) {
		case NODE_LABEL: {
			return (node) -> {
				if (searchLabel != null) {
					String lbl = node.getNodeLabel();
					if (lbl != null) {
						return lbl.equals(searchLabel);
					}
				}
				return clazz.isInstance(node.underlyingAstNode);
			};
		}

		case REFLECTION:
		default: {
			return (node) -> clazz.isInstance(node.underlyingAstNode);
		}
		}
	}
}
