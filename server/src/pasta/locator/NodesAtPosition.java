package pasta.locator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;

import pasta.AstInfo;
import pasta.ast.AstNode;
import pasta.metaprogramming.InvokeProblem;

public class NodesAtPosition {

	public static List<JSONObject> get(AstInfo info, AstNode astNode, int pos) {
		List<JSONObject> ret = new ArrayList<>();
		getTo(ret, info, astNode, pos, 0);
		Collections.reverse(ret); // Narrowest/smallest node first inthe list
		return ret;
	}

	private static void getTo(List<JSONObject> out, AstInfo info, AstNode astNode, int pos, int depth) {
		final Span nodePos;
		try {
			nodePos = astNode.getRecoveredSpan(info);
		} catch (InvokeProblem e1) {
			e1.printStackTrace();
			return;
		}
		if ((nodePos.start <= pos && nodePos.end >= pos)) {
			
			// Default false for List/Opt, they are very rarely useful
			boolean show = !astNode.isList() && !astNode.isOpt();

			final Boolean override = astNode.pastaVisible();
			if (override != null ? override : show) {
				out.add(CreateLocator.fromNode(info, astNode));
			}
		}
		for (AstNode child : astNode.getChildren()) {
			getTo(out, info, child, pos, depth + 1);
		}
	}
}
