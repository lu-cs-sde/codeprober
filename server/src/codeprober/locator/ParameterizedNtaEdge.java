package codeprober.locator;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.ast.AstNode;
import codeprober.protocol.ParameterValue;

public class ParameterizedNtaEdge extends NodeEdge {
	public ParameterizedNtaEdge(AstNode sourceNode, TypeAtLoc sourceLoc, AstNode targetNode, TypeAtLoc targetLoc,
			String ntaName, List<ParameterValue> arguments) {
		super(sourceNode, sourceLoc, targetNode, targetLoc, NodeEdgeType.FN, buildMthObj(ntaName, arguments));
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