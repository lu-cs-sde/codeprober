package codeprober.locator;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.ParameterType;
import codeprober.protocol.create.CreateType;

public class AttrsInNode {

	public static JSONArray get(AstInfo info, AstNode node, List<String> whitelistFilter) {
		if (whitelistFilter == null) {
			 whitelistFilter = new ArrayList<>();
		} else {
			whitelistFilter = new ArrayList<>(whitelistFilter);
		}
		whitelistFilter.addAll(Arrays.asList(new String[]{ "getChild", "getParent", "getNumChild" }));
//		final Pattern illegalNamePattern = Pattern.compile(".*(\\$|_).*");
		
		List<JSONObject> attrs = new ArrayList<>();
		for (Method m : node.underlyingAstNode.getClass().getMethods()) {
//			if (!Modifier.isPublic(m.getModifiers())) {
//				continue;
//			}
			if (!MethodKindDetector.isSomeAttr(m) && !whitelistFilter.contains(m.getName())) {
				continue;
			}
//			if (illegalNamePattern.matcher(m.getName()).matches()) {
//				continue;
//			}
			final Parameter[] parameters = m.getParameters();
			final ParameterType[] types = CreateType.fromParameters(info, parameters);
			if (types == null) {
//				System.out.println("Skipping " + m + " due to unknown param types");
				continue;
			}
			JSONObject attr = new JSONObject();
			attr.put("name", m.getName());

			JSONArray args = new JSONArray();

			for (int i = 0; i < parameters.length; ++i) {
				final JSONObject arg = types[i].toJson();
				arg.put("name", parameters[i].getName());
				args.put(arg);
			}
			attr.put("args", args);

			attrs.add(attr);
		}

		attrs.sort((a, b) -> a.getString("name").compareTo(b.getString("name")));
		return new JSONArray(attrs);
	}

	public static List<String> extractFilter(AstInfo info, AstNode node) {
		final String mth = "cpr_propertyListShow";
		if (!info.hasOverride0(node.underlyingAstNode.getClass(), mth)) {
			return null;
		}
		try {
			final Object override = Reflect.invoke0(node.underlyingAstNode, mth);
			if (override instanceof Collection<?>) {
				@SuppressWarnings("unchecked")
				final Collection<String> cast = (Collection<String>) override;
				return new ArrayList<String>(cast);
			} else {
				System.out.println("'" + mth + "' is expected to be a collection, got " + override);
			}
		} catch (InvokeProblem e) {
			System.out.println("Error when evaluating " + mth);
			e.printStackTrace();
		}
		return null;
	}
}
