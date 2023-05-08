package codeprober.locator;

import java.util.ArrayList;
import java.util.List;

import codeprober.AstInfo;
import codeprober.ast.AstNode;

public class NodesWithProperty {

	public static List<Object> get(AstInfo info, AstNode astNode, String propName, int limitNumberOfNodes) {
		List<Object> ret = new ArrayList<>();
		ret.add("Foo"); // Reserve first slot
		int totalNumNodes = limitNumberOfNodes - getTo(ret, info, astNode, propName, limitNumberOfNodes);
		if (totalNumNodes == 0) {
			ret.clear();
		} else if (totalNumNodes > limitNumberOfNodes) {
			ret.set(0, String.format("%s\n%s\n%s", //
					"Found " + totalNumNodes + " nodes with '" + propName +"', limited output to " + limitNumberOfNodes + ".", //
					"This limit can be configured with the environment variable:", //
					"  `QUERY_PROBE_OUTPUT_LIMIT=NUM`"));
		} else {
			ret.set(0, "Found " + totalNumNodes + " nodes");
		}
		return ret;
	}

	private static int getTo(List<Object> out, AstInfo info, AstNode astNode, String propName,
			int remainingNodeBudget) {

		// Default false for List/Opt, they are very rarely useful
		boolean show = !astNode.isList() && !astNode.isOpt();
		final Boolean override = astNode.showInPropertySearchProbe(info, propName);
		if (override != null ? override : show) {
			if (astNode.hasProperty(info, propName)) {
				--remainingNodeBudget;
				if (remainingNodeBudget >= 0) {
					out.add(astNode);
				}
			}
		}
		for (AstNode child : astNode.getChildren(info)) {
			remainingNodeBudget = getTo(out, info, child, propName, remainingNodeBudget);
		}
		return remainingNodeBudget;
	}
}
