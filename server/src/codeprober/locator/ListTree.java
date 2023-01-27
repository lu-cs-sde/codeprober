package codeprober.locator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.NodeEdge.NodeEdgeType;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;

public class ListTree {

	private static JSONObject listDownwardsWithInjects(AstInfo info, AstNode node, int budget,
			IdentityHashMap<Object, List<JSONObject>> nodesToForceExploreAndAddExtraChildren) {
		final JSONObject ent = new JSONObject();

		ent.put("type", "node");
		ent.put("locator", CreateLocator.fromNode(info, node));

		final List<JSONObject> extraChildren = nodesToForceExploreAndAddExtraChildren.get(node.underlyingAstNode);
		if (node.hasSomeChildVisibleInAstView(info) || extraChildren != null) {
			if (budget <= 0 && extraChildren == null) {
				ent.put("children", new JSONObject() //
						.put("type", "placeholder") //
						.put("num", node.getNumChildren(info)));
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
					if (!child.shouldBeVisibleInAstView()
							&& !nodesToForceExploreAndAddExtraChildren.containsKey(child.underlyingAstNode)) {
						continue;
					}
					final JSONObject childObj = listDownwardsWithInjects(info, child, budget - 1,
							nodesToForceExploreAndAddExtraChildren);
					String name = underlyingAstNodeToName.get(child.underlyingAstNode);
					if (name != null) {
						childObj.put("name", name);
					}
					children.put(childObj);
				}
				if (extraChildren != null) {
					children.putAll(extraChildren);
				}
				ent.put("children", children);
			}
		}
		return ent;

	}

	public static JSONObject listDownwards(AstInfo info, AstNode node, int budget) {
		return listDownwardsWithInjects(info, node, budget, new IdentityHashMap<>());
	}

	public static JSONObject listUpwards(AstInfo info, AstNode node) {
		final IdentityHashMap<Object, List<JSONObject>> forceExploreAndInjects = new IdentityHashMap<>();

		final List<NodeEdge> edges = CreateLocator.getEdgesTo(info, node);
		if (edges == null) {
			System.out.println("Failed extracting edges to " + node);
			return null;
		}
		int budget = 1;
		for (int i = edges.size() - 1; i >= 0; --i) {
			final NodeEdge edge = edges.get(i);
			if (edge.type == NodeEdgeType.FN) {
				// Normal downwards listing would not follow this edge. We must do it ourselves
				JSONObject obj = listDownwardsWithInjects(info, edge.targetNode, budget + 1, forceExploreAndInjects);
				obj.put("type", "fn");
				obj.put("name", edge.toJson().getJSONObject("value").getString("name"));
				final ArrayList<JSONObject> extraChildren = new ArrayList<>();
				extraChildren.add(obj);
				forceExploreAndInjects.put(edge.sourceNode.underlyingAstNode, extraChildren);
				budget = 1;
			} else {
				forceExploreAndInjects.put(edge.sourceNode.underlyingAstNode, new ArrayList<>());
				++budget;
			}
		}

		return listDownwardsWithInjects(info, edges.isEmpty() ? node : edges.get(0).sourceNode, budget,
				forceExploreAndInjects);
	}
}
