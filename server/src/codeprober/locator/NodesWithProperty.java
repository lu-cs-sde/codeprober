package codeprober.locator;

import java.util.ArrayList;
import java.util.List;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.metaprogramming.TypeIdentificationStyle;

public class NodesWithProperty {

	private enum Comparison {
		EQ, NEQ, CONTAINS, SUBTYPE;

		public String toString() {
			switch (this) {
			default:
			case EQ:
				return "=";
			case NEQ:
				return "!=";
			case CONTAINS:
				return "~=";
			case SUBTYPE:
				return "<:";
			}
		}
	}

	static class Predicate {
		public final String lhs;
		public final Comparison comp;
		public final String rhs;
		public final Class<?> rhsAsClass;

		public Predicate(AstInfo info, String predPart) throws ClassNotFoundException {
			final int subtypePos = predPart.indexOf("<:");
			if (subtypePos > 0) {
				this.lhs = predPart.substring(0, subtypePos).trim();
				this.comp = Comparison.SUBTYPE;
				this.rhs = predPart.substring(subtypePos + "<:".length()).trim();
				if (info.typeIdentificationStyle == TypeIdentificationStyle.NODE_LABEL) {
					this.rhsAsClass = null;
				} else {
					Class<?> expectedCls = null;
					final ClassLoader classLoader = info.ast.underlyingAstNode.getClass().getClassLoader();
					try {
						expectedCls = classLoader.loadClass(this.rhs);
					} catch (ClassNotFoundException e) {
						if (!this.rhs.contains(".")) {
							final Class<?> rootClass = info.ast.underlyingAstNode.getClass();
							// Might be shorthand syntax, retry with the ast package prefixed
							final String desugaredClsName = String.format("%s%s", //
									rootClass.getEnclosingClass() != null //
											? (rootClass.getEnclosingClass().getName() + "$")
											: (rootClass.getPackage().getName() + "."), //
									this.rhs //
							);
							expectedCls = classLoader.loadClass(desugaredClsName);

						} else {
							throw e;
						}
					}
					this.rhsAsClass = expectedCls;
				}
				return;
			}

			this.rhsAsClass = null;
			final int eqPos = predPart.indexOf('=');
			if (eqPos <= 0) {
				this.lhs = predPart;
				this.comp = Comparison.EQ;
				this.rhs = "true";
				return;
			}
			String key = predPart.substring(0, eqPos).trim();
			if (key.endsWith("!")) {
				this.lhs = key.substring(0, key.length() - 1).trim();
				this.comp = Comparison.NEQ;
			} else if (key.endsWith("~")) {
				this.lhs = key.substring(0, key.length() - 1).trim();
				this.comp = Comparison.CONTAINS;
			} else {
				this.lhs = key;
				this.comp = Comparison.EQ;
			}
			this.rhs = (eqPos == predPart.length() - 1) ? "" : predPart.substring(eqPos + 1).trim();
		}

		public String toString() {
			if (comp == Comparison.EQ && "true".equals(rhs)) {
				return lhs;
			}
			return String.format("%s%s%s", lhs, comp.toString(), rhs);
		}
	}

	public static List<Object> get(AstInfo info, AstNode astNode, String propName, String predicates,
			int limitNumberOfNodes) {
		List<Object> ret = new ArrayList<>();
		ret.add("Foo"); // Reserve first slot
		final List<Predicate> parsedPredicates;
		try {
			parsedPredicates = parsePredicates(info, predicates);
		} catch (ClassNotFoundException e) {
			System.out.println("Failed parsing predicates, invalid subtype in " + predicates);
			ret.set(0, "Invalid subtype predicate");
			return ret;
		}

		int totalNumNodes = limitNumberOfNodes
				- getTo(ret, info, astNode, propName, parsedPredicates, limitNumberOfNodes);
		if (totalNumNodes == 0) {
			ret.clear();
		} else if (totalNumNodes > limitNumberOfNodes) {
			ret.set(0, String.format("%s\n%s\n%s", //
					"Found " + totalNumNodes + " node" + (totalNumNodes == 1 ? "" : "s") + " with '" + propName
							+ "', limited output to " + limitNumberOfNodes + ".", //
					"This limit can be configured with the environment variable:", //
					"  `QUERY_PROBE_OUTPUT_LIMIT=NUM`"));
		} else {
			ret.set(0, "Found " + totalNumNodes + " node" + (totalNumNodes == 1 ? "" : "s"));
		}
		return ret;
	}

	static List<Predicate> parsePredicates(AstInfo info, String fullPredicateStr) throws ClassNotFoundException {
		final List<Predicate> ret = new ArrayList<>();
		boolean escape = false;
		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i < fullPredicateStr.length(); ++i) {
			final char ch = fullPredicateStr.charAt(i);
			if (escape) {
				escape = false;
				switch (ch) {
				case 'n':
					builder.append('\n');
					break;
				case '&':
					builder.append('&');
					break;
				case '\\': {
					builder.append('\\');
					break;
				}
				default: {
					System.err.println("Invalid escape character '" + ch + "'");
					builder.append(ch);
					break;
				}
				}
				continue;
			}
			switch (ch) {
			case '\\': {
				escape = true;
				break;
			}
			case '&': {
				ret.add(new Predicate(info, builder.toString().trim()));
				builder.delete(0, builder.length());
				break;
			}
			default: {
				builder.append(ch);
			}
			}
		}
		final String tail = builder.toString().trim();
		if (tail.length() > 0) {
			ret.add(new Predicate(info, tail));
		}

		return ret;
	}

	public static Object invokePotentiallyLabelled(AstInfo info, AstNode node, String property) {
		if (property.startsWith("l:")) {
			return Reflect.invokeN(node.underlyingAstNode, "cpr_lInvoke", new Class[] { String.class },
					new Object[] { property.substring("l:".length()) });
		}
		if (property.startsWith("@")) {
			switch (property) {
			case "@lineSpan": {
				return node.getRecoveredSpan(info);
			}
			default: {
				System.err.println("Invalid magic '@'-property in search query: '" + property + "'");
			}
			}
		}
		return Reflect.invoke0(node.underlyingAstNode, property);
	}

	private static int getTo(List<Object> out, AstInfo info, AstNode astNode, String propName,
			List<Predicate> predicates, int remainingNodeBudget) {

		// Default false for List/Opt, they are very rarely useful
		boolean show = !astNode.isList() && !astNode.isOpt();
		if (predicates != null && !predicates.isEmpty()) {
			show = true;

			for (Predicate pred : predicates) {
				try {
					if (pred.comp == Comparison.SUBTYPE) {
						AstNode actualNode;
						final Object actualValue;
						switch (pred.lhs) {

						case "this": {
							actualNode = astNode;
							actualValue = astNode.underlyingAstNode;
							break;
						}
						default: {
							actualNode = null;
							actualValue = invokePotentiallyLabelled(info, astNode, pred.lhs);
							break;
						}
						}
						if (info.typeIdentificationStyle == TypeIdentificationStyle.NODE_LABEL) {
							if (actualNode == null) {
								actualNode = new AstNode(actualValue);

							}
							final String label = actualNode.getNodeLabel();
							if (label != null && label.equals(pred.rhs)) {
								continue;
							}
							show = false;
							break;

						} else {
							if (pred.rhsAsClass.isInstance(actualValue)) {
								continue;
							}
							show = false;
							break;
						}
					}
					if (pred.comp == Comparison.CONTAINS && pred.lhs.equals("@lineSpan")) {
						// This mean 'span cover the specified line', handle separately
						try {
							show = astNode.getRecoveredSpan(info).containsLine(Integer.parseInt(pred.rhs));
							if (!show) {
								// No need to go deeper
								return remainingNodeBudget;
							}
						} catch (NumberFormatException e) {
							System.err.println("Expected value '" + pred.rhs + "' for line span is not an integer");
							show = false;
							break;
						}
						continue;
					}

					final String strInvokeVal = String.valueOf(invokePotentiallyLabelled(info, astNode, pred.lhs))
							.trim();
					if (pred.comp == Comparison.CONTAINS) {
						show = strInvokeVal.contains(pred.rhs);
					} else {
						show = strInvokeVal.equals(pred.rhs);
						if (pred.comp == Comparison.NEQ) {
							show = !show;
						}
					}
					if (!show) {
						break;
					}
				} catch (InvokeProblem e) {
					// If somebody doesn't implement the predicate, exclude them
					show = false;
					break;
				}
			}
		} else {
			final Boolean override = astNode.showInPropertySearchProbe(info, propName);
			if (override != null) {
				show = override;
			}
		}
		if (show) {
			if (propName.equals("") || astNode.hasProperty(info, propName)) {
				--remainingNodeBudget;
				if (remainingNodeBudget >= 0) {
					out.add(astNode);
				}
			}
		}
		for (AstNode child : astNode.getChildren(info)) {
			remainingNodeBudget = getTo(out, info, child, propName, predicates, remainingNodeBudget);
		}
		return remainingNodeBudget;
	}
}
