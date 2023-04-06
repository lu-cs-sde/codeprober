package codeprober.locator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.create.CreateType;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;

public class AttrsInNode {

	public static List<Property> getTyped(AstInfo info, AstNode node, List<String> whitelistFilter,
			boolean includeAll) {
		final List<Property> ret = new ArrayList<>();
		if (whitelistFilter == null) {
			whitelistFilter = new ArrayList<>();
		} else {
			whitelistFilter = new ArrayList<>(whitelistFilter);
		}
		whitelistFilter
				.addAll(Arrays.asList(new String[] { "getChild", "getParent", "getNumChild", "toString", "dumpTree" }));
//		final Pattern illegalNamePattern = Pattern.compile(".*(\\$|_).*");

		for (Method m : node.underlyingAstNode.getClass().getMethods()) { // getMethods() rather than
																			// getDeclaredMethods() to only get public
																			// methods
			if (!includeAll && !MethodKindDetector.looksLikeAUserAccessibleJastaddRelatedMethod(m)
					&& !whitelistFilter.contains(m.getName())) {
				continue;
			}
			final List<PropertyArg> args = CreateType.fromParameters(info, m.getParameters());
			if (args == null) {
				System.out.println("skip due to bad param types " + m.getName());
				continue;
			}

			ret.add(new Property(m.getName(), args, MethodKindDetector.getAstChildName(m)));
		}
		for (String filter : whitelistFilter) {
			if (filter.startsWith("l:")) {
				// Special "label-invoke" method, always add it
				ret.add(new Property(filter, new ArrayList<>(), null));
			}
		}

		ret.sort((a, b) -> a.name.compareTo(b.name));
		return ret;
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
