package pasta.protocol.create;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;

import pasta.AstInfo;
import pasta.locator.CreateLocator;
import pasta.metaprogramming.Reflect;

public class EncodeResponseValue {

	public static void encode(AstInfo info, JSONArray out, Object value, HashSet<Object> alreadyVisitedNodes) {
		if (value == null) {
			out.put("null");
//			new IdentityHashMap<>()
			return;
		}
		// Clone to avoid showing 'already visited' when this encoding 'branch' hasn't
		// visited at all
		alreadyVisitedNodes = new HashSet<Object>(alreadyVisitedNodes);

		if (value != null && info.basAstClazz.isInstance(value)) {
			if (alreadyVisitedNodes.contains(value)) {
				out.put("<< reference loop to already visited value " + value + " >>");
				return;
			}
			try {
				Object preferredView = Reflect.throwingInvoke0(value, "pastaView");
				alreadyVisitedNodes.add(value);
				encode(info, out, preferredView, alreadyVisitedNodes);
				return;
			} catch (NoSuchMethodException | InvocationTargetException e) {
				// Fall down to default view
			}

			try {
				final JSONObject locator = CreateLocator.fromNode(info, value);
				if (locator != null) {
					JSONObject wrapper = new JSONObject();
					wrapper.put("type", "node");
					wrapper.put("value", locator);
					out.put(wrapper);

					out.put("\n");
					if (value.getClass().getSimpleName().equals("List")) {
						final int numEntries = (Integer) Reflect.invoke0(value, "getNumChild");
						out.put("");
						if (numEntries == 0) {
							out.put("<empty list>");
						} else {
							out.put("List contents [" + numEntries + "]:");
							for (Object child : (Iterable<?>) Reflect.invoke0(value, "astChildren")) {
								alreadyVisitedNodes.add(value);
								encode(info, out, child, alreadyVisitedNodes);
							}
						}
						return;
					}
					return;
				}
			} catch (NoSuchMethodException | InvocationTargetException e) {
				// No getStart - this isn't an AST Node!
				// It is just some class that happens to reside in the same package
				// Fall down to default toString encoding below
			}
		}

		if (value instanceof Iterable<?>) {
			if (alreadyVisitedNodes.contains(value)) {
				out.put("<< reference loop to already visited value " + value + " >>");
				return;
			}
			alreadyVisitedNodes.add(value);

			JSONArray indent = new JSONArray();
			Iterable<?> iter = (Iterable<?>) value;
			for (Object o : iter) {
				encode(info, indent, o, alreadyVisitedNodes);
			}
			out.put(indent);
			return;
		}
		if (value instanceof Iterator<?>) {
			if (alreadyVisitedNodes.contains(value)) {
				out.put("<< reference loop to already visited value " + value + " >>");
				return;
			}
			alreadyVisitedNodes.add(value);
			JSONArray indent = new JSONArray();
			Iterator<?> iter = (Iterator<?>) value;
			while (iter.hasNext()) {
				encode(info, indent, iter.next(), alreadyVisitedNodes);
			}
			out.put(indent);
			return;
		}
//		if (value instanceof Object[]) {
//			if (alreadyVisitedNodes.contains(value)) {
//				out.put("<< reference loop to already visited value " + value + " >>");
//				return;
//			}
//			alreadyVisitedNodes.add(value);
//			
//			final JSONArray indent = new JSONArray();
//			for (Object child : (Object[])value) {
//				encodeValue(indent, child, baseAstType, recoveryStrategy, alreadyVisitedNodes, false);
//			}
//			final JSONObject indentObj = new JSONObject();
//			indentObj.put("type", "indent");
//			indentObj.put("value", indent);
//			out.put(indentObj);
//			return;
//		}
		try {
			if (value.getClass().getMethod("toString").getDeclaringClass() == Object.class) {
//				if (value.getClass().isEnum()) {
//					out.put(value.toString());
//				}
				out.put("No toString() or pastaView() implementation in " + value.getClass().getName());
			}
		} catch (NoSuchMethodException e) {
			System.err.println("No toString implementation for " + value.getClass());
			e.printStackTrace();
		}
		for (String line : (value + "").split("\n")) {
			out.put(line);
		}
	}
}
