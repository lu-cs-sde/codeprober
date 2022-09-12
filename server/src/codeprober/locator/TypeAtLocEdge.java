package codeprober.locator;

import org.json.JSONObject;

import codeprober.ast.AstNode;

public class TypeAtLocEdge extends NodeEdge {
	public TypeAtLocEdge(AstNode sourceNode, TypeAtLoc sourceLoc, AstNode targetNode, TypeAtLoc targetLoc, int depth) {
		super(sourceNode, sourceLoc, targetNode, targetLoc, NodeEdgeType.TypeAtLoc, buildLocatorObj(targetLoc, depth, targetNode));
	}

	private static JSONObject buildLocatorObj(TypeAtLoc target, int depth, AstNode targetNode) {
		final JSONObject locatorObj = new JSONObject();
		locatorObj.put("start", target.loc.start);
		locatorObj.put("end", target.loc.end);
		locatorObj.put("depth", depth);
		CreateLocator.putNodeTypeName(locatorObj, targetNode.underlyingAstNode);
		return locatorObj;
	}

}