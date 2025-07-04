package codeprober.locator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.CreateLocator.Edges;
import codeprober.locator.CreateLocator.StepWithTarget;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.data.ListedTreeChildNode;
import codeprober.protocol.data.ListedTreeNode;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.TALStep;

public class DataDrivenListTree {

	private static ListedTreeNode listDownwardsWithInjects(AstInfo info, AstNode node, String name, int budget,
			IdentityHashMap<Object, List<ListedTreeNode>> nodesToForceExploreAndAddExtraChildren) {
//		final JSONObject ent = new JSONObject();

//		final NodeLocator locator = NodeLocator.fromJSON(CreateLocator.fromNode(info, node));
//		ListedTreeNode ltn = new ListedTreeNode(null, locator, null, null)
//		ent.put("type", "node");
//		ent.put("locator", CreateLocator.fromNode(info, node));
		final NodeLocator locator = CreateLocator.fromNode(info, node);

		final String astLabel = node.getAstLabel();
		if (astLabel != null) {
			if (name != null) {
				name = String.format("%s - \"%s\"", name, astLabel);
			} else {
				name = String.format("\"%s\"", astLabel);
			}
		}

//		UnionOfChildTreeNodesPlaceholderNode chilr
		final List<ListedTreeNode> extraChildren = nodesToForceExploreAndAddExtraChildren.get(node.underlyingAstNode);
		if (node.hasSomeChildVisibleInAstView(info) || extraChildren != null) {
			if (budget <= 0 && extraChildren == null) {
				return new ListedTreeNode(locator, name,
						ListedTreeChildNode.fromPlaceholder(node.getNumChildren(info)));
			} else {
				final List<ListedTreeNode> children = new ArrayList<>();
				final Map<Object, String> underlyingAstNodeToName = new IdentityHashMap<>();
				for (Method m : node.underlyingAstNode.getClass().getMethods()) {
					if (m.getParameterCount() != 0) {
						continue;
					}
					final String astName = MethodKindDetector.getAstChildName(m);
					if (astName != null) {
						try {
							underlyingAstNodeToName.put(Reflect.invoke0(node.underlyingAstNode, m.getName()), astName);
						} catch (InvokeProblem ip) {
							// Ignore
						}
					}
				}
				if (info.hasOverride1(node.underlyingAstNode.getClass(), "cpr_lGetChildName", String.class)) {
					for (String prop : node.propertyListShow(info)) {
						if (!prop.startsWith("l:")) {
							continue;
						}
						final String withoutPrefix = prop.substring("l:".length());
						try {
							final String childName = (String) Reflect.invokeN(node.underlyingAstNode,
									"cpr_lGetChildName", new Class[] { String.class }, new Object[] { withoutPrefix });
							if (childName == null) {
								continue;
							}
							final Object nodeVal = Reflect.invokeN(node.underlyingAstNode, "cpr_lInvoke",
									new Class[] { String.class }, new Object[] { withoutPrefix });
							if (nodeVal != null) {
								underlyingAstNodeToName.put(nodeVal, childName);
							}

						} catch (InvokeProblem | ClassCastException e) {
							System.out.println("Error invoking cpr_lGetChildName and/or cpr_lInvoke");
							e.printStackTrace();
						}
					}
				}
				final Map<Object, Boolean> normalChildren = new IdentityHashMap<>();

				for (AstNode child : node.getChildren(info)) {
					if (!child.shouldBeVisibleInAstView()
							&& !nodesToForceExploreAndAddExtraChildren.containsKey(child.underlyingAstNode)) {
						continue;
					}
					normalChildren.put(child.underlyingAstNode, true);
					children.add(
							listDownwardsWithInjects(info, child, underlyingAstNodeToName.get(child.underlyingAstNode),
									budget - 1, nodesToForceExploreAndAddExtraChildren));
				}
				if (extraChildren != null) {
					children.addAll(extraChildren);
				}

				List<NodeLocator> remoteRefs = null;
				for (String extra : node.extraAstReferences(info)) {
					try {
						Object extraVal = NodesWithProperty.invokePotentiallyLabelled(info, node, extra);
						if (extraVal == null) {
							continue;
						}
						if (!info.baseAstClazz.isInstance(extraVal)) {
							System.err.println("Expected AST node from extra AST reference '" + "', got: " + extraVal);
							continue;
						}
						final AstNode extraNode = new AstNode(extraVal);
						final NodeLocator extraLoc = CreateLocator.fromNode(info, extraNode);
						if (extraLoc == null) {
							continue;
						}
						final AstNode extraParent = extraNode.parent();
						final String trimmedExtra = extra.startsWith("l:") ? extra.substring(2) : extra;
						if (extraParent != null && extraParent.underlyingAstNode == node.underlyingAstNode
								&& !normalChildren.containsKey(extraVal)) {
							// Local NTA/HOA.
							children.add(
									new ListedTreeNode(extraLoc, trimmedExtra, ListedTreeChildNode.fromPlaceholder(0)));
						} else {
							// Remote reference
							if (remoteRefs == null) {
								remoteRefs = new ArrayList<>();
							}
							// Inject the property name in the "label" of the node locator
							remoteRefs.add(new NodeLocator(new TALStep(extraLoc.result.type, trimmedExtra,
									extraLoc.result.start, extraLoc.result.end, extraLoc.result.depth),
									extraLoc.steps));
						}
					} catch (InvokeProblem ip) {
						System.err.println("Failed invoking 'extra' AST reference " + extra);
					}
				}
				return new ListedTreeNode(locator, name, ListedTreeChildNode.fromChildren(children), remoteRefs);
			}
		}

		return new ListedTreeNode(locator, name, ListedTreeChildNode.fromChildren( //
				extraChildren == null ? new ArrayList<>() : extraChildren //
		));
	}

	public static ListedTreeNode listDownwards(AstInfo info, AstNode node, int budget) {
		return listDownwardsWithInjects(info, node, null, budget, new IdentityHashMap<>());
	}

	public static ListedTreeNode listUpwards(AstInfo info, AstNode node) {
		final IdentityHashMap<Object, List<ListedTreeNode>> forceExploreAndInjects = new IdentityHashMap<>();

		final Edges edges = CreateLocator.getEdgesTo(info, node);
		if (edges == null) {
			System.out.println("Failed extracting edges to " + node);
			return null;
		}
		int budget = 1;
		for (int i = edges.mergedEdges.size() - 1; i >= 0; --i) {
			final StepWithTarget swt = edges.mergedEdges.get(i);
			if (swt.step.isNta()) {
				// Normal downwards listing would not follow this edge. We must do it ourselves
				ListedTreeNode obj = listDownwardsWithInjects(info, swt.target, swt.step.asNta().property.name,
						budget + 1, forceExploreAndInjects);
				final List<ListedTreeNode> extraChildren = new ArrayList<>();
				extraChildren.add(obj);
				forceExploreAndInjects.put(swt.source.underlyingAstNode, extraChildren);
				budget = 1;
			} else {
				forceExploreAndInjects.put(swt.source.underlyingAstNode, new ArrayList<>());
				++budget;
			}
		}

		return listDownwardsWithInjects(info, edges.mergedEdges.isEmpty() ? node : edges.mergedEdges.get(0).source,
				null, budget, forceExploreAndInjects);
	}
}
