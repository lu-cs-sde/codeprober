package codeprober.locator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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

	public static JSONArray get(AstInfo info, AstNode node, List<String> whitelistFilter, boolean includeAll) {
		if (whitelistFilter == null) {
			whitelistFilter = new ArrayList<>();
		} else {
			whitelistFilter = new ArrayList<>(whitelistFilter);
		}
		whitelistFilter.addAll(Arrays.asList(new String[] { "getChild", "getParent", "getNumChild", "toString", "dumpTree" }));
//		final Pattern illegalNamePattern = Pattern.compile(".*(\\$|_).*");

		List<JSONObject> attrs = new ArrayList<>();
		for (Method m : node.underlyingAstNode.getClass().getMethods()) { // getMethods() rather than getDeclaredMethods() to only get public methods
			if (!includeAll && !MethodKindDetector.looksLikeAUserAccessibleJastaddRelatedMethod(m)
					&& !whitelistFilter.contains(m.getName())) {

				if (m.getName().equals("debugMePls")) {
					System.out.println("skip due to everything " + m.getName());
					for (Annotation a : m.getAnnotations()) {
						System.out.println(a.annotationType().getName());
					}
					System.out.println("^---- dumped annotations.... wtf");
					System.out.println(m.getAnnotations().length);
					System.out.println(m.getDeclaredAnnotations().length);
				}
				continue;
			}
			System.out.println("include " + m.getName() +"; annotation len: " + m.getAnnotations().length);
			final Parameter[] parameters = m.getParameters();
			final ParameterType[] types = CreateType.fromParameters(info, parameters);
			if (types == null) {
				System.out.println("skip due to bad param types " + m.getName());
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
			
			final String astChildName = MethodKindDetector.getAstChildName(m);
			if (astChildName != null) {
				attr.put("astChildName", astChildName);
			}

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
			} else if (override instanceof Object[]) {
				final String[] cast = (String[]) override;
				return Arrays.asList(cast);
			} else {
				System.out.println("'" + mth + "' is expected to be a collection or String array, got " + override);
			}
		} catch (InvokeProblem e) {
			System.out.println("Error when evaluating " + mth);
			e.printStackTrace();
		}
		return null;
	}
}
