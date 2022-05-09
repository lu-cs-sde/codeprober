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
		getTo(ret, info, astNode, pos);
		Collections.reverse(ret); // Narrowest/smallest node first inthe list
		return ret;
	}

	private static void getTo(List<JSONObject> out, AstInfo info, AstNode astNode, int pos) {
		final Span nodePos;
		try {
			nodePos = Span.extractPosition(info, astNode);
		} catch (InvokeProblem e1) {
			e1.printStackTrace();
			return;
		}
		if ((nodePos.start <= pos && nodePos.end >= pos)) {
			
			// Default false for List/Opt, they are very rarely useful
			boolean show = !astNode.isList() && !astNode.isOpt();

			final Boolean override = astNode.pastaVisible();
			if (override != null ? override : show) {
				JSONObject obj = new JSONObject();
				obj.put("start", nodePos.start);
				obj.put("end", nodePos.end);
				obj.put("type", astNode.underlyingAstNode.getClass().getSimpleName());
				out.add(obj);
			}
		}
		for (AstNode child : astNode.getChildren()) {
			getTo(out, info, child, pos);
		}
	}
}
