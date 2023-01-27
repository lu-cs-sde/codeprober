package codeprober.locator;

import org.json.JSONObject;

import codeprober.ast.AstNode;

/**
 * Represents a step from a parent to a child node.
 */
abstract class NodeEdge {

	public static enum NodeEdgeType {
		ChildIndex("child"), FN("nta"), TypeAtLoc("tal");

		private String jsonRepresentation;

		private NodeEdgeType(String jsonRepresentation) {
			this.jsonRepresentation = jsonRepresentation;

		}
	}

	public final AstNode sourceNode;
	public final TypeAtLoc sourceLoc;

	public final AstNode targetNode;
	public final TypeAtLoc targetLoc;
	public final NodeEdgeType type;
	public final Object value;

	public NodeEdge(AstNode sourceNode, TypeAtLoc sourceLoc, AstNode targetNode, TypeAtLoc targetLoc, NodeEdgeType type,
			Object value) {
		this.sourceNode = sourceNode;
		this.sourceLoc = sourceLoc;
		this.targetNode = targetNode;
		this.targetLoc = targetLoc;
		this.type = type;
		this.value = value;
	}

	public boolean canBeCollapsed() {
		return targetLoc.loc.isMeaningful() && type == NodeEdgeType.TypeAtLoc;
	}

	public JSONObject toJson() {
		final JSONObject obj = new JSONObject();
		obj.put("type", type.jsonRepresentation);
		obj.put("value", value);
		return obj;
	}

	@Override
	public String toString() {
		return type + "<" + targetNode + ">";
	}
}
