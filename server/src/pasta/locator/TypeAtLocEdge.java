package pasta.locator;

import org.json.JSONObject;

import pasta.ast.AstNode;
import pasta.locator.NodeEdge.NodeEdgeType;

public class TypeAtLocEdge extends NodeEdge {
	public TypeAtLocEdge(AstNode sourceNode, TypeAtLoc sourceLoc, AstNode targetNode, TypeAtLoc targetLoc) {
		super(sourceNode, sourceLoc, targetNode, targetLoc, NodeEdgeType.TypeAtLoc, buildLocatorObj(targetLoc));
	}

	private static JSONObject buildLocatorObj(TypeAtLoc target) {
		final JSONObject locatorObj = new JSONObject();
		locatorObj.put("start", target.loc.start);
		locatorObj.put("end", target.loc.end);
		locatorObj.put("type", target.type);
		return locatorObj;
	}

}