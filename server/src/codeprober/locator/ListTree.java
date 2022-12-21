package codeprober.locator;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.ast.AstNode;

public class ListTree {

	public static JSONObject list(AstInfo info, AstNode node, int budget) {
		final JSONObject ent = new JSONObject();

		ent.put("type", "node");
		ent.put("locator", CreateLocator.fromNode(info, node));

		final int numChildren = node.getNumChildren(info);
		if (numChildren > 0) {
			if (budget <= 0) {
				ent.put("children", new JSONObject() //
						.put("type", "placeholder") //
						.put("num", numChildren));
			} else {
				final JSONArray children = new JSONArray();
				for (AstNode child : node.getChildren(info)) {
					children.put(list(info, child, budget - 1));
				}
				ent.put("children", children);
			}
		}
		return ent;
	}
}
