package pasta.locator;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import pasta.protocol.ParameterValue;

/**
 * Represents a step from a parent to a child node.
 */
abstract class NodeEdge {

	public final TypeAtLoc source;
	public final TypeAtLoc target;
	public final String type;
	public final Object value;

	public NodeEdge(TypeAtLoc source, TypeAtLoc target, String type, Object value) {
		this.source = source;
		this.target = target;
		this.type = type;
		this.value = value;
	}

	public boolean canBeCollapsed() {
		return target.loc.isMeaningful() && type.equals("tal");
	}

	public JSONObject toJson() {
		final JSONObject obj = new JSONObject();
		obj.put("type", type);
		obj.put("value", value);
		return obj;
	}

	public static class ChildIndexEdge extends NodeEdge {
		public ChildIndexEdge(TypeAtLoc source, TypeAtLoc target, int childIndex) {
			super(source, target, "child", childIndex);
		}
	}

	public static class ParameterizedNtaEdge extends NodeEdge {
		public ParameterizedNtaEdge(TypeAtLoc source, TypeAtLoc target, String ntaName,
				List<ParameterValue> arguments) {
			super(source, target, "nta", buildMthObj(ntaName, arguments));
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
		public TypeAtLocEdge(TypeAtLoc source, TypeAtLoc target) {
			super(source, target, "tal", buildLocatorObj(target));
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
