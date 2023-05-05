package codeprober.locator;

import java.util.ArrayList;
import java.util.List;

import codeprober.AstInfo;
import codeprober.ast.AstNode;

public class NodesWithProperty {

	public static List<Object> get(AstInfo info, AstNode astNode, String propName) {
		List<Object> ret = new ArrayList<>();
		getTo(ret, info, astNode, propName);
		return ret;
	}

	private static void getTo(List<Object> out, AstInfo info, AstNode astNode, String propName) {

		// Default false for List/Opt, they are very rarely useful
		boolean show = !astNode.isList() && !astNode.isOpt();
		final Boolean override = astNode.showInPropertySearchProbe(info, propName);
		if (override != null ? override : show) {
			if (astNode.hasProperty(info, propName)) {
				out.add(astNode);
			}
		}
		for (AstNode child : astNode.getChildren(info)) {
			getTo(out, info, child, propName);
		}
	}
}
