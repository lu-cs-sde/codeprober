package codeprober.locator;

import java.lang.invoke.MethodType;
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

	public static enum BaseInclusionFilter {
		ATTRIBUTES_ONLY, //
		ALMOST_ALL_NORMAL_METHODS, //
		ALL_METHODS_INCLUDING_BOXED_PRIMITIVES_AND_VARARGS,
	}

	public static List<Property> getTyped(AstInfo info, AstNode node, List<String> whitelistFilter,
			BaseInclusionFilter baseFilter) {
		final List<Property> ret = new ArrayList<>();
		if (whitelistFilter == null) {
			whitelistFilter = new ArrayList<>();
		} else {
			whitelistFilter = new ArrayList<>(whitelistFilter);
		}
		whitelistFilter
				.addAll(Arrays.asList(new String[] { "getChild", "getParent", "getNumChild", "toString", "dumpTree" }));

		final boolean includeAll = baseFilter != BaseInclusionFilter.ATTRIBUTES_ONLY;

		for (Method m : node.underlyingAstNode.getClass().getMethods()) { // getMethods() rather than
																			// getDeclaredMethods() to only get public
																			// methods
			if (!includeAll && !MethodKindDetector.looksLikeAUserAccessibleJastaddRelatedMethod(m)
					&& !whitelistFilter.contains(m.getName())) {
				continue;
			}
			List<PropertyArg> args = CreateType.fromParameters(info, m.getParameters());
			if (args == null) {
				if (baseFilter == BaseInclusionFilter.ALL_METHODS_INCLUDING_BOXED_PRIMITIVES_AND_VARARGS
						&& m.isVarArgs()) {
					List<Class<?>> rewritten = new ArrayList<>();
					final Class<?>[] formalParams = m.getParameterTypes();
					for (int i = 0; i < formalParams.length - 1; ++i) {
						rewritten.add(unboxPrimitive(formalParams[i]));
					}
					final Class<?> varComponent = formalParams[formalParams.length - 1].getComponentType();
					rewritten.add(unboxPrimitive(varComponent));
					args = CreateType.fromClasses(info, rewritten.toArray(new Class<?>[rewritten.size()]));
					if (args != null) {
						args.set(args.size() - 1, PropertyArg.fromAny(PropertyArg.fromString(varComponent.getName())));
					}
				}
//				System.out.println("skip due to bad param types " + m.getName());
				if (args == null) {
					continue;
				}
			}

			ret.add(new Property(m.getName(), args, MethodKindDetector.getAstChildName(m),
					MethodKindDetector.getRelatedAspect(m)));
		}
		final boolean canDescribeChildNames = info.hasOverride1(node.underlyingAstNode.getClass(), "cpr_lGetChildName",
				String.class);
		final boolean canDescribeLabeledProperties = info.hasOverride1(node.underlyingAstNode.getClass(),
				"cpr_lGetAspectName", String.class);
		for (String filter : whitelistFilter) {
			if (filter.startsWith("l:")) {
				// Special "label-invoke" method, always add it
				String childName = null;
				if (canDescribeChildNames) {
					try {
						childName = (String) Reflect.invokeN(node.underlyingAstNode, "cpr_lGetChildName",
								new Class[] { String.class }, new Object[] { filter.substring("l:".length()) });
					} catch (InvokeProblem | ClassCastException e) {
						System.out.println("Error invoking cpr_lGetChildName");
						e.printStackTrace();
					}
				}
				String relatedAspect = null;
				if (canDescribeLabeledProperties && childName == null) {
					try {
						relatedAspect = (String) Reflect.invokeN(node.underlyingAstNode, "cpr_lGetAspectName",
								new Class[] { String.class }, new Object[] { filter.substring("l:".length()) });
					} catch (InvokeProblem | ClassCastException e) {
						System.out.println("Error invoking cpr_lGetAspectName");
						e.printStackTrace();
					}
				}
				ret.add(new Property(filter, new ArrayList<>(), childName, relatedAspect));
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

	private static Class<?> unboxPrimitive(Class<?> primitive) {
		return MethodType.methodType(primitive).unwrap().returnType();
	}
}
