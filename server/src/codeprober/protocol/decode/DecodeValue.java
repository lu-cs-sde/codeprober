package codeprober.protocol.decode;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.locator.ApplyLocator;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.metaprogramming.StreamInterceptor;
import codeprober.protocol.ParameterTypeDetail;
import codeprober.protocol.ParameterValue;

public abstract class DecodeValue {

	public static ParameterValue decode(AstInfo info, JSONObject serializedForm, JSONArray streamArgCapturesDst) {
		final String argTypeStr = serializedForm.getString("type");
		switch (argTypeStr) {
		case "java.lang.String": {
			// 'optString' to allow nullable values
			return new ParameterValue(String.class, ParameterTypeDetail.NORMAL, info,
					serializedForm.optString("value"));
		}
		case "int": {
			return new ParameterValue(Integer.TYPE, ParameterTypeDetail.NORMAL, info, serializedForm.getInt("value"));
		}
		case "boolean": {
			return new ParameterValue(Boolean.TYPE, ParameterTypeDetail.NORMAL, info,
					serializedForm.getBoolean("value"));
		}
		case "java.util.Collection": {
			final Collection<Object> coll = new ArrayList<>();
			final JSONArray arr = serializedForm.getJSONArray("value");
			for (int i = 0; i < arr.length(); i++) {
				final ParameterValue childEntry = decode(info, arr.getJSONObject(i), streamArgCapturesDst);
				if (childEntry == null) {
					System.out.println("Failed decoding value " + i + " in collection");
					return null;
				}
				coll.add(childEntry);
			}
			return new ParameterValue(Collection.class, ParameterTypeDetail.NORMAL, info, coll);
		}
		case "java.io.OutputStream": {
			return new ParameterValue(OutputStream.class, ParameterTypeDetail.OUTPUTSTREAM, info, null);
		}

		default: {
			final String detailStr = serializedForm.getString("detail");
			final ParameterTypeDetail detail = ParameterTypeDetail.decode(detailStr);
			if (detail == null) {
				System.out.println("Unknown attribute detail '" + detailStr + "'");
				return null;
			}
			switch (detail) {
			case AST_NODE:
				Class<?> type = info.loadAstClass.apply(argTypeStr);

				if (serializedForm.isNull("value")) {
					return new ParameterValue(type, detail, info, null);
				} else {
					final ResolvedNode locatedArg = ApplyLocator.toNode(info, serializedForm.getJSONObject("value"));
					if (locatedArg == null) {
						System.out.println("Couldn't find node arg in the document. Try remaking the probe");
						return null;
					}
					return new ParameterValue(type, detail, info, locatedArg.node);
				}

			case OUTPUTSTREAM:
				return new ParameterValue(info.loadAstClass.apply(argTypeStr), detail, info,
						new StreamInterceptor(new PrintStream(new ByteArrayOutputStream())) {

							@Override
							protected void onLine(String line) {
//								System.out.println(line);
								final JSONObject fmt = new JSONObject();
								fmt.put("type", "stream-arg");
								fmt.put("value", line);
								streamArgCapturesDst.put(fmt);

							}
						});

			default:
				System.out.println("Unknown attribute type '" + argTypeStr + "'");
				return null;
			}
		}
		}
	}
}
