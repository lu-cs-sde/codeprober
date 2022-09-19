package codeprober.protocol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.CreateLocator;

public class ParameterValue extends ParameterType {

	private final AstInfo info;
	private final Object value;

	public ParameterValue(Class<?> paramType, ParameterTypeDetail detail, AstInfo info, Object value) {
		super(paramType, detail);
		this.info = info;
		this.value = value;
		if (value instanceof Collection) {
			final Collection<?> c = (Collection<?>) value;
			for (Object child : c) {
				if (!(child instanceof ParameterValue)) {
					throw new Error(
							"Illegal parameter value, collections must only contain other ParameterValue instances");
				}
			}
		}
	}

	public Object getUnpackedValue() {
		if (value == null) {
			return null;
		}
		switch (detail) {
		case AST_NODE:
			return ((AstNode) value).underlyingAstNode;

		case OUTPUTSTREAM:
			return value;

		case NORMAL: // Fall through
		default:
			if (value instanceof Collection) {
				final List<Object> recursivelyUnpacked = new ArrayList<>();
				for (Object child : ((Collection<?>) value)) {
					recursivelyUnpacked.add(((ParameterValue) child).getUnpackedValue());
				}
				return recursivelyUnpacked;
			}
			return value;
		}
	}

	public void serializeTo(JSONObject out) {
		super.serializeTo(out);

		if (value == null) {
			out.put("value", JSONObject.NULL);
		} else {

			switch (detail) {
			case AST_NODE:
				out.put("value", CreateLocator.fromNode(info, (AstNode) value));
				break;

			case OUTPUTSTREAM:
				out.put("value", JSONObject.NULL);
				break;

			case NORMAL: // Fall through
			default:
				if (value instanceof Collection) {
					final Collection<?> c = (Collection<?>) value;
					JSONArray arr = new JSONArray();
					for (Object child : c) {
						arr.put(((ParameterValue) child).toJson());
					}
					out.put("value", arr);
				} else {
					out.put("value", value);
				}
				break;
			}
		}
	}

}
