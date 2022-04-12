package pasta;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

public class RobustNodeId {
	public final TypeAtLoc root;
	public final List<Edge> steps;

	public RobustNodeId(TypeAtLoc root, List<Edge> steps) {
		this.root = root;
		this.steps = steps;
	}

	public static RobustNodeId extract(Object astNode, PositionRecoveryStrategy recoveryStrategy) {
		final List<Edge> res = new ArrayList<>();

		try {
			extractStepsTo(astNode, res, recoveryStrategy);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
				| NoSuchMethodException | InvocationTargetException e) {
			e.printStackTrace();
		}

		if (res.isEmpty()) {
			// Root node!
			try {
				return new RobustNodeId(TypeAtLoc.from(astNode, recoveryStrategy), Collections.emptyList());
			} catch (NoSuchMethodException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}

		Collections.reverse(res);
		TypeAtLoc root = res.get(0).source;

		for (int i = 0; i < res.size(); i++) {
			Edge e = res.get(i);
			if (e.canBeCollapsed()) {
				root = e.target;
				res.remove(i);
				--i;
			} else {
				// Assume the following graph (left-to-right):
				// A tal B nta C tal D tal E nta F tal G
				// It would be safe to remove quite a lot, down do:
				// B nta C tal E nta F tal G
				// This loop prunes after non-removable nodes, e.g 'C tal D'
				while (i < res.size() - 2 && res.get(i + 1).canBeCollapsed() && res.get(i + 2).canBeCollapsed()) {
					res.remove(i + 1);
				}
				// Collapse inner nodes as well
//				for (int j = i + 1; j < res.size() - 1; j++) {
//					Edge inner = res.get(j);
//
//					// If we have
//					// A -ch-> B -ch-> C -nta-> D
//					// Then A -ch-> B -ch-> C can be collapsed to B -ch-> C
//					// But only because two collapsible steps happen in a row.
//					// If there is only one collapsible step then removing it will cause issues for
//					// lookup up later. For example:
//					// A -ch-> B -nta-> C
//					// If we remove A -ch-> B then we will later try to lookup an nta on A, which is
//					// wrong
//					if (inner.canBeCollapsed() && res.get(j + 1).canBeCollapsed()) {
//						res.remove(j);
//						--j;
//					}
//				}
				break;
			}
//			if (res.get(i).canBeCollapsed()) {
//				trimStart = i;
//			} else {
//				break;
//			}
		}

//		if (trimStart != -1) {
//			for (int i = 0; i <= trimStart; i++) {
//				res.remove(i);
//			}
//		}
		return new RobustNodeId(root, res);
	}

	private static boolean isNta(Method m) {
		if (!Modifier.isPublic(m.getModifiers())) {
			return false;
		}

		for (Annotation a : m.getAnnotations()) {
			if (a.annotationType().getName().endsWith(".ASTNodeAnnotation$Attribute")) {
				return (Boolean) Reflect.invoke0(a, "isNTA");
			}
		}
		return false;
	}

	private static void extractStepsTo(Object astNode, List<Edge> out, PositionRecoveryStrategy recoveryStrategy)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException,
			NoSuchMethodException, InvocationTargetException {
		final Object parent;
		try {
			parent = Reflect.getParent(astNode);
		} catch (NoSuchMethodException | InvocationTargetException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		if (parent == null) {
			// No edge
			return;
		}

		Consumer<Edge> addEdge = (id) -> {
			out.add(id);

//			out.add("TODO edge " //
//					+ (parent != null ? parent.getClass().getSimpleName() : "null") //
//					+ " --" + id + "-->" //
//					+ astNode.getClass().getSimpleName());

		};
		final TypeAtLoc source = TypeAtLoc.from(parent, recoveryStrategy);
		final TypeAtLoc target = TypeAtLoc.from(astNode, recoveryStrategy);
		int childIdx = 0;
		for (Object child : (Iterable<?>) Reflect.invoke0(parent, "astChildren")) {
			if (child == astNode) {
				if (target.position.isMeaningful()) {
					addEdge.accept(new Edge.TypeAtLocEdge(source, target));
				} else {
					addEdge.accept(new Edge.ChildIndexEdge(source, target, childIdx));
				}
				extractStepsTo(parent, out, recoveryStrategy);
				return;
			}
			++childIdx;
		}
		for (Method m : parent.getClass().getMethods()) {
//			if (!m.getName().equals("unknownDecl")) {
//				// Hack for development, REMOVEME
//				continue;
//			}
			if (!isNta(m)) {
				continue;
			}

			// What if the NTA is a List? Like Program.predefinedFunctions()
			// What if the NTA takes 1 parameter?
			// What if >= 2 parameters?
			if (m.getParameterCount() == 0) {
				final Field field = m.getDeclaringClass().getDeclaredField(m.getName() + "_value");
				field.setAccessible(true);
				final Object cachedNtaValue = field.get(parent);
				// Intentional identity comparison
				if (cachedNtaValue == astNode) {
					addEdge.accept(new Edge.ZeroArgNtaEdge(source, target, m.getName()));
					extractStepsTo(parent, out, recoveryStrategy);
					return;
				}
			}
		}

		if ((Integer) Reflect.invoke0(parent, "getNumChild") == 0) {
			// Strange proxy node that appears in NTA+param cases
			// RealParent -> Proxy -> Value
			// In this case, 'parent' is the proxy. We want the grandparent instead
			final Object realParent = Reflect.getParent(parent);
			if (realParent != null) {
				for (Method m : realParent.getClass().getMethods()) {
					if (!isNta(m)) {
						continue;
					}
					if (m.getParameterCount() == 0) {
						continue;
					}
					Field f;
					String guessedCacheName = m.getName();
					for (Parameter p : m.getParameters()) {
						guessedCacheName += "_" + p.getType().getSimpleName();
					}
					guessedCacheName += "_values";
					try {
						f = m.getDeclaringClass().getDeclaredField(guessedCacheName);
					} catch (NoSuchFieldException e) {
						System.out.println(
								"Failed to guess values field name for " + m + ", thought it was " + guessedCacheName);
						e.printStackTrace();
						continue;
					}
					f.setAccessible(true);
					@SuppressWarnings("unchecked")
					final Map<Object, Object> cache = (Map<Object, Object>) f.get(realParent);

					for (Entry<Object, Object> ent : cache.entrySet()) {
						if (ent.getValue() == astNode) {
							final Object param = ent.getKey();

							if (param.getClass() == String.class) {
								final TypeAtLoc realSource = TypeAtLoc.from(realParent, recoveryStrategy);
								addEdge.accept(new Edge.ParameterizedNtaEdge(realSource, target, m.getName(),
										Arrays.asList(param.toString())));
								extractStepsTo(realParent, out, recoveryStrategy);
								return;
							} else {
								System.out.println("Found parameterized NTA edge from " + m.getName()
										+ ", but it takes param '" + param + "' (type=" + param.getClass()
										+ "), not sure how to encode it");
							}

						}
					}
				}
			}

		}

		System.out.println("Unknown edge " + parent + " -->" + astNode);
		System.out.println("other way: " + source + " --> " + target);
		System.out.println("Parent pretty : " + Reflect.invoke0(parent, "prettyPrint"));
		System.out.println("child type " + astNode.getClass());
		final Field childIndex = astNode.getClass().getDeclaredField("childIndex");
		childIndex.setAccessible(true);
		System.out.println("value childIndex : " + childIndex.getInt(astNode));

		Object search = parent;
		while (search != null) {
			search = Reflect.getParent(search);
			System.out.println("Grandparent.. " + search);
		}
//		addEdge.accept("UNKNOWN EDGE");
//		if (parent != null) {
//			extractStepsTo(parent, out);
//		}
	}
}
