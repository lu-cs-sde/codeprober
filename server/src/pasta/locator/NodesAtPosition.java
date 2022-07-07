package pasta.locator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;

import pasta.AstInfo;
import pasta.ast.AstNode;
import pasta.metaprogramming.InvokeProblem;
import pasta.metaprogramming.Reflect;

public class NodesAtPosition {

	public static List<JSONObject> get(AstInfo info, AstNode astNode, int pos) {
		List<JSONObject> ret = new ArrayList<>();
		getTo(ret, info, astNode, pos);
		Collections.reverse(ret); // Narrowest/smallest node first inthe list
		return ret;
	}

	private static void getTo(List<JSONObject> out, AstInfo info, AstNode astNode, int pos) {
		final Span nodePos;
		try {
			nodePos = astNode.getRecoveredSpan(info);
		} catch (InvokeProblem e1) {
			e1.printStackTrace();
			return;
		}
		final Boolean cutoff = astNode.cutoffPastaVisibleTree(info);
		if (cutoff != null && cutoff) {
//			System.out.println("Cutoff at ");
			return;
		}
		if (out.isEmpty() || (nodePos.start <= pos && nodePos.end >= pos)) {

			// Default false for List/Opt, they are very rarely useful
			boolean show = !astNode.isList() && !astNode.isOpt();

			final Boolean override = astNode.pastaVisible(info);
			if (override != null ? override : show) {
				out.add(CreateLocator.fromNode(info, astNode));
			}
		}
		if (astNode == info.ast) {
			
			// Root node, maybe skip ahead
			Object next = null;
			try {
				next = Reflect.invoke0(astNode.underlyingAstNode, "pastaVisibleNextAfterRoot");
			} catch (InvokeProblem e) {
				// OK, this is an optional attribute
			}
			if (next != null) {
				getTo(out, info, new AstNode(next), pos);
				return;
			}
		}
		for (AstNode child : astNode.getChildren()) {
			getTo(out, info, child, pos);
		}
	}
}
