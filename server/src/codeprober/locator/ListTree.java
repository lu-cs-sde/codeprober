package codeprober.locator;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;

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
				final Map<Object, String> underlyingAstNodeToName = new HashMap<>();
				for (Method m : node.underlyingAstNode.getClass().getMethods()) {
					if (m.getParameterCount() != 0) {
						continue;
					}
					String astName = MethodKindDetector.getAstChildName(m);
					if (astName != null) {
						try {
							underlyingAstNodeToName.put(Reflect.invoke0(node.underlyingAstNode, m.getName()), astName);
						} catch (InvokeProblem ip) {
							// Ignore
						}
					}
				}
				for (AstNode child : node.getChildren(info)) {
					final JSONObject childObj = list(info, child, budget - 1);
					String name = underlyingAstNodeToName.get(child.underlyingAstNode);
					if (name != null) {
						childObj.put("name", name);
					}
					children.put(childObj);
				}
				ent.put("children", children);
			}
		}
		return ent;
	}
}
