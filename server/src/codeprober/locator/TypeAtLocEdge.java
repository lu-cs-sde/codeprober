package codeprober.locator;

import org.json.JSONObject;

import codeprober.ast.AstNode;

public class TypeAtLocEdge extends NodeEdge {
	public TypeAtLocEdge(AstNode sourceNode, TypeAtLoc sourceLoc, AstNode targetNode, TypeAtLoc targetLoc, int depth) {
		super(sourceNode, sourceLoc, targetNode, targetLoc, NodeEdgeType.TypeAtLoc, buildLocatorObj(targetLoc, depth));
	}

	private static JSONObject buildLocatorObj(TypeAtLoc target, int depth) {
		final JSONObject locatorObj = new JSONObject();
		locatorObj.put("start", target.loc.start);
		locatorObj.put("end", target.loc.end);
		locatorObj.put("depth", depth);
		locatorObj.put("type", target.type);
		return locatorObj;
	}

}