package codeprober.locator;

import java.util.ArrayList;
import java.util.List;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;

public class NodesWithProperty {

	public static List<Object> get(AstInfo info, AstNode astNode, String propName, String predicate,
			int limitNumberOfNodes) {
		List<Object> ret = new ArrayList<>();
		ret.add("Foo"); // Reserve first slot
		int totalNumNodes = limitNumberOfNodes - getTo(ret, info, astNode, propName, predicate, limitNumberOfNodes);
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

	static List<String> parsePredicates(String fullPredicateStr) {
		final List<String> ret = new ArrayList<>();
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
				ret.add(builder.toString().trim());
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
			ret.add(tail);
		}

		return ret;
	}

	private static int getTo(List<Object> out, AstInfo info, AstNode astNode, String propName, String predicate,
			int remainingNodeBudget) {

		// Default false for List/Opt, they are very rarely useful
		boolean show = !astNode.isList() && !astNode.isOpt();
		if (predicate != null) {
			show = true;
			for (String predPart : parsePredicates(predicate)) {
				predPart = predPart.trim();
				try {
					final int subtypePos = predPart.indexOf("<:");
					if (subtypePos > 0) {

						final String expression = predPart.substring(0, subtypePos).trim();
						final String expectedValue = predPart.substring(subtypePos + "<:".length()).trim();

						final Object actualValue;
						switch (expression) {
						case "this": {
							actualValue = astNode.underlyingAstNode;
							break;
						}
						default: {
							actualValue = Reflect.invoke0(astNode.underlyingAstNode, expression);
							break;
						}
						}
						if (actualValue == null) {
							show = false;
							break;
						}
						try {
							Class<?> expectedCls = null;
							final ClassLoader classLoader = info.ast.underlyingAstNode.getClass().getClassLoader();
							try {
								expectedCls = classLoader.loadClass(expectedValue);
							} catch (ClassNotFoundException e) {
								if (!expectedValue.contains(".")) {
									final Class<?> rootClass = info.ast.underlyingAstNode.getClass();
									// Might be shorthand syntax, retry with the ast package prefixed
									final String desugaredClsName = String.format("%s%s", //
											rootClass.getEnclosingClass() != null //
													? (rootClass.getEnclosingClass().getName() + "$")
													: (rootClass.getPackage().getName() + "."), //
											expectedValue //
									);
									expectedCls = classLoader.loadClass(desugaredClsName);

								} else {
									throw e;
								}
							}
//							actualValue.getClass().asSubclass(expectedCls); // Will throw CCE if not correct subtype
							if (expectedCls.isInstance(actualValue)) {
								continue;
							}
							show = false;
							break;
						} catch (ClassNotFoundException e) {
							System.out.println("Invalid expected class in subtype predicate: " + expectedValue);
							e.printStackTrace();
							show = false;
							break;
//						} catch (ClassCastException e) {
//							// Does not implement the requested type, continue
//							show = false;
//							break;
						}
					}
					final int eqPos = predPart.indexOf('=');
					if (eqPos <= 0) {
						show = (boolean) Reflect.invoke0(astNode.underlyingAstNode, predPart);
					} else {
						String key = predPart.substring(0, eqPos).trim();
						boolean contains = false;
						boolean invert = false;
						if (key.endsWith("!")) {
							invert = true;
							key = key.substring(0, key.length() - 1).trim();
						} else if (key.endsWith("~")) {
							contains = true;
							key = key.substring(0, key.length() - 1).trim();
						}
						final String expected = (eqPos == predPart.length() - 1) ? ""
								: predPart.substring(eqPos + 1).trim();
						final String strInvokeVal = String.valueOf(Reflect.invoke0(astNode.underlyingAstNode, key))
								.trim();
						if (contains) {
							show = strInvokeVal.contains(expected);
						} else {
							show = expected.equals(strInvokeVal);
						}
						if (invert) {
							show = !show;
						}
					}
					if (!show) {
						break;
					}
				} catch (ClassCastException e) {
					System.err.println("Expected predicate '" + predPart + "' to return boolean");
					e.printStackTrace();
					show = false;
					break;
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
			remainingNodeBudget = getTo(out, info, child, propName, predicate, remainingNodeBudget);
		}
		return remainingNodeBudget;
	}
}
