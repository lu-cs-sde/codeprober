package pasta.locator;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import pasta.AstInfo;
import pasta.ast.AstNode;
import pasta.metaprogramming.InvokeProblem;
import pasta.metaprogramming.Reflect;
import pasta.protocol.ParameterType;
import pasta.protocol.create.CreateType;

public class AttrsInNode {

	public static JSONArray get(AstInfo info, AstNode node, List<String> blacklistFilter) {
		List<JSONObject> attrs = new ArrayList<>();
		for (Method m : node.underlyingAstNode.getClass().getMethods()) {
			if (blacklistFilter != null && !blacklistFilter.contains(m.getName())) {
				continue;
			}
			if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
				continue;
			}
			if (Pattern.compile(".*(\\$|_).*").matcher(m.getName()).matches()) {
				continue;
			}
			final Parameter[] parameters = m.getParameters();
			final ParameterType[] types = CreateType.fromParameters(info, parameters);
			if (types == null) {
				System.out.println("Skipping " + m + " due to unknown param types");
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
		try {
			final Object override = Reflect.invoke0(node.underlyingAstNode, "pastaAttrs");
			if (override instanceof Collection<?>) {
				@SuppressWarnings("unchecked")
				final Collection<String> cast = (Collection<String>) override;
				return new ArrayList<String>(cast);
			} else {
				System.out.println("'pastaAttrs' is expected to be a collection, got " + override);
			}
		} catch (InvokeProblem e) {
			if (e.getCause() instanceof NoSuchMethodException) {
				// Ignore, this is expected in many cases
			} else {
				System.out.println("Error when evaluating pastaAttrs");
				e.printStackTrace();
			}
		}
		return null;
	}
}
