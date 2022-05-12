package pasta.protocol.decode;

import java.util.ArrayList;
import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONObject;

import pasta.AstInfo;
import pasta.locator.ApplyLocator;
import pasta.locator.ApplyLocator.ResolvedNode;
import pasta.protocol.ParameterValue;

public abstract class DecodeValue {

	public static ParameterValue decode(AstInfo info, JSONObject serializedForm) {
		final String argTypeStr = serializedForm.getString("type");
		switch (argTypeStr) {
		case "java.lang.String": {
			// 'optString' to allow nullable values
			return new ParameterValue(String.class, false, info, serializedForm.optString("value"));
		}
		case "int": {
			return new ParameterValue(Integer.TYPE, false, info, serializedForm.getInt("value"));
		}
		case "boolean": {
			return new ParameterValue(Boolean.TYPE, false, info, serializedForm.getBoolean("value"));
		}
		case "java.util.Collection": {
			final Collection<Object> coll = new ArrayList<>();
			final JSONArray arr = serializedForm.getJSONArray("value");
			for (int i = 0; i < arr.length(); i++) {
				final ParameterValue childEntry = decode(info, arr.getJSONObject(i));
				if (childEntry == null) {
					System.out.println("Failed decoding value " + i + " in collection");
					return null;
				}
				coll.add(childEntry);
			}
			return new ParameterValue(Collection.class, false, info, coll);
		}

		default: {
			if (serializedForm.getBoolean("isNodeType")) {
				Class<?> type = info.loadAstClass.apply(argTypeStr);

				if (serializedForm.isNull("value")) {
					return new ParameterValue(type, true, info, null);
				} else {
					final ResolvedNode locatedArg = ApplyLocator.toNode(info, serializedForm.getJSONObject("value"));
					if (locatedArg == null) {
						System.out.println("Couldn't find node arg in the document. Try remaking the probe");
						return null;
					}
					return new ParameterValue(type, true, info, locatedArg.node);
				}
			} else {
				System.out.println("Unknown attribute type '" + argTypeStr + "'");
				return null;
			}
		}
		}
	}
}
