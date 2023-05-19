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
					"Found " + totalNumNodes + " nodes with '" + propName + "', limited output to " + limitNumberOfNodes
							+ ".", //
					"This limit can be configured with the environment variable:", //
					"  `QUERY_PROBE_OUTPUT_LIMIT=NUM`"));
		} else {
			ret.set(0, "Found " + totalNumNodes + " nodes");
		}
		return ret;
	}

	private static int getTo(List<Object> out, AstInfo info, AstNode astNode, String propName, String predicate,
			int remainingNodeBudget) {

		// Default false for List/Opt, they are very rarely useful
		boolean show = !astNode.isList() && !astNode.isOpt();
		if (predicate != null) {
			show = true;
			for (String predPart : predicate.split(",")) {
				predPart = predPart.trim();
				try {
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
						final String expected = (eqPos == predPart.length() - 1) ? "" : predPart.substring(eqPos + 1).trim();
						final String strInvokeVal = String.valueOf(Reflect.invoke0(astNode.underlyingAstNode, key)).trim();
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
			if (astNode.hasProperty(info, propName)) {
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
