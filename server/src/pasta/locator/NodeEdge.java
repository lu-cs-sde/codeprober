package pasta.locator;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import pasta.ast.AstNode;
import pasta.protocol.ParameterValue;

/**
 * Represents a step from a parent to a child node.
 */
abstract class NodeEdge {

	public static enum NodeEdgeType {
		ChildIndex("child"), NTA("nta"), TypeAtLoc("tal");

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

	public static class ChildIndexEdge extends NodeEdge {
		public ChildIndexEdge(AstNode sourceNode, TypeAtLoc sourceLoc, AstNode targetNode, TypeAtLoc targetLoc,
				int childIndex) {
			super(sourceNode, sourceLoc, targetNode, targetLoc, NodeEdgeType.ChildIndex, childIndex);
		}
	}

	public static class ParameterizedNtaEdge extends NodeEdge {
		public ParameterizedNtaEdge(AstNode sourceNode, TypeAtLoc sourceLoc, AstNode targetNode, TypeAtLoc targetLoc,
				String ntaName, List<ParameterValue> arguments) {
			super(sourceNode, sourceLoc, targetNode, targetLoc, NodeEdgeType.NTA, buildMthObj(ntaName, arguments));
		}

		private static JSONObject buildMthObj(String ntaName, List<ParameterValue> arguments) {
			final JSONObject ret = new JSONObject();
			ret.put("name", ntaName);

			final JSONArray arr = new JSONArray();
			for (ParameterValue arg : arguments) {
				arr.put(arg.toJson());
			}
			ret.put("args", arr);

			return ret;
		}
	}

	public static class TypeAtLocEdge extends NodeEdge {
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
}
