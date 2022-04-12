package pasta;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public abstract class Edge {

	public final TypeAtLoc source;
	public final TypeAtLoc target;

	public Edge(TypeAtLoc source, TypeAtLoc target) {
		this.source = source;
		this.target = target;
	}

	public boolean canBeCollapsed() {
		return target.position.isMeaningful();
	}

	public abstract void encode(JSONArray out);
//	String toString();

	public static class ChildIndexEdge extends Edge {
		public final int childIndex;

		public ChildIndexEdge(TypeAtLoc source, TypeAtLoc target, int childIndex) {
			super(source, target);
			this.childIndex = childIndex;
		}

		@Override
		public String toString() {
			return "child:" + childIndex;
		}
		
		@Override
		public boolean canBeCollapsed() {
			return false;
		}

		@Override
		public void encode(JSONArray out) {
			final JSONObject obj = new JSONObject();
			obj.put("type", "child");
			obj.put("value", childIndex);
			out.put(obj);
		}
	}

	public static class ZeroArgNtaEdge extends Edge {
		public final String ntaName;

		public ZeroArgNtaEdge(TypeAtLoc source, TypeAtLoc target, String ntaName) {
			super(source, target);
			this.ntaName = ntaName;
		}

		@Override
		public String toString() {
			return "nta:" + ntaName;
		}

		@Override
		public boolean canBeCollapsed() {
			return false;
		}

		@Override
		public void encode(JSONArray out) {
			final JSONObject obj = new JSONObject();
			obj.put("type", "znta");
			obj.put("value", ntaName);
			out.put(obj);
		}
	}

	public static class ParameterizedNtaEdge extends Edge {
		public final String ntaName;
		public final List<String> arguments;

		public ParameterizedNtaEdge(TypeAtLoc source, TypeAtLoc target, String ntaName, List<String> arguments) {
			super(source, target);
			this.ntaName = ntaName;
			this.arguments = arguments;
		}

		@Override
		public String toString() {
			return "nta:" + ntaName + "(" + arguments + ")";
		}

		@Override
		public boolean canBeCollapsed() {
			return false;
		}

		@Override
		public void encode(JSONArray out) {
			final JSONObject obj = new JSONObject();
			obj.put("type", "pnta");
			
			final JSONArray arr = new JSONArray();
			for (String arg : arguments) {
				arr.put(arg);
			}

			final JSONObject value = new JSONObject();
			value.put("name", ntaName);
			value.put("args", arr);
			obj.put("value", value);
			
			out.put(obj);
		}
	}

	public static class TypeAtLocEdge extends Edge {
		public TypeAtLocEdge(TypeAtLoc source, TypeAtLoc target) {
			super(source, target);
		}

		@Override
		public void encode(JSONArray out) {
			final JSONObject obj = new JSONObject();
			obj.put("type", "tloc");

			final JSONObject locatorObj = new JSONObject();
			locatorObj.put("start", target.position.start);
			locatorObj.put("end", target.position.end);
			locatorObj.put("type", target.type);

			obj.put("value", locatorObj);

			out.put(obj);
		}
	}
}
