package codeprober.locator;

import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.ast.AstNode;

public class TypeAtLocEdge extends NodeEdge {

	public TypeAtLocEdge(AstInfo info, AstNode sourceNode, AstNode targetNode, int depth, boolean isExternal) {
		this(sourceNode, TypeAtLoc.from(info, sourceNode), targetNode, TypeAtLoc.from(info, targetNode), depth, isExternal);
	}

	public TypeAtLocEdge(AstNode sourceNode, TypeAtLoc sourceLoc, AstNode targetNode, TypeAtLoc targetLoc, int depth, boolean isExternal) {
		super(sourceNode, sourceLoc, targetNode, targetLoc, NodeEdgeType.TypeAtLoc, buildLocatorObj(targetLoc, depth, targetNode, isExternal));
	}


	private static JSONObject buildLocatorObj(TypeAtLoc target, int depth, AstNode targetNode, boolean isExternal) {
		final JSONObject locatorObj = new JSONObject();
		locatorObj.put("start", target.loc.start);
		locatorObj.put("end", target.loc.end);
		locatorObj.put("depth", depth);
		if (isExternal) {
			locatorObj.put("external", true);
		}
		CreateLocator.putNodeTypeValues(targetNode, locatorObj);
		return locatorObj;
	}

}