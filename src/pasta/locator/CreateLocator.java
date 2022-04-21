package pasta.locator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import pasta.AstInfo;
import pasta.metaprogramming.Reflect;
import pasta.protocol.ParameterValue;
import pasta.protocol.create.CreateValue;

public class CreateLocator {

	private static class Locator {
		public final TypeAtLoc root;
		public final List<NodeEdge> steps;

		public Locator(TypeAtLoc root, List<NodeEdge> steps) {
			this.root = root;
			this.steps = steps;
		}
	}

	public static JSONObject fromNode(AstInfo info, Object astNode)
			throws NoSuchMethodException, InvocationTargetException {
		// TODO add position recovery things here(?)
		final Locator id;

		try {
			id = extract(info, astNode);
		} catch (RuntimeException e) {
			System.out.println("Failed to extract locator for " + astNode);
			e.printStackTrace();
			return null;
		}

		final JSONObject robustRoot = new JSONObject();
		robustRoot.put("start", id.root.loc.start);
		robustRoot.put("end", id.root.loc.end);
		robustRoot.put("type", id.root.type);

		final JSONObject robustResult = new JSONObject();
		final Span astPos = Span.extractPosition(astNode, info.recoveryStrategy);
		robustResult.put("start", astPos.start);
		robustResult.put("end", astPos.end);
		robustResult.put("type", astNode.getClass().getSimpleName());

		final JSONArray steps = new JSONArray();
		for (NodeEdge step : id.steps) {
			steps.put(step.toJson());
		}

		final JSONObject robust = new JSONObject();
		robust.put("root", robustRoot);
		robust.put("result", robustResult);
		robust.put("steps", steps);
//		System.out.println("Locator: " + robust.toString(2));
		return robust;
	}

	public static Locator extract(AstInfo info, Object astNode) {
		final List<NodeEdge> res = new ArrayList<>();

		try {
			extractStepsTo(info, astNode, res);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
				| NoSuchMethodException | InvocationTargetException e) {
			e.printStackTrace();
		}

		if (res.isEmpty()) {
			// Root node!
			try {
				return new Locator(TypeAtLoc.from(astNode, info.recoveryStrategy), Collections.emptyList());
			} catch (NoSuchMethodException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}

		Collections.reverse(res);
		TypeAtLoc root = res.get(0).source;

		for (int i = 0; i < res.size(); i++) {
			NodeEdge e = res.get(i);
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
				break;
			}
		}

		return new Locator(root, res);
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

	private static void extractStepsTo(AstInfo info, Object astNode, List<NodeEdge> out)
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

		final TypeAtLoc source = TypeAtLoc.from(parent, info.recoveryStrategy);
		final TypeAtLoc target = TypeAtLoc.from(astNode, info.recoveryStrategy);
		int childIdxCounter = 0;
		int foundTargetIndex = -1;
		boolean ambiguousTarget = false;
		for (Object child : (Iterable<?>) Reflect.invoke0(parent, "astChildren")) {
			if (child == astNode) {
				foundTargetIndex = childIdxCounter;
			} else {
				ambiguousTarget |= target.equals(TypeAtLoc.from(child, info.recoveryStrategy));
			}
			++childIdxCounter;
		}
		if (foundTargetIndex != -1) {
			// Ambiguous TAL edges can occur for example in NTAs. Assume we have:
			// nta Foo.bar() { return new List().add(new A()).add(new A()); }
			// That NTA creates a list of two A's. The two A's are missing location information and have the same position
			// If we use a TAL edge then we cannot distinguish between them in the future.
			if (target.loc.isMeaningful() && !ambiguousTarget) {
				out.add(new NodeEdge.TypeAtLocEdge(source, target));
			} else {
				out.add(new NodeEdge.ChildIndexEdge(source, target, foundTargetIndex));
			}
			extractStepsTo(info, parent, out);
			return;
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
					out.add(new NodeEdge.ParameterizedNtaEdge(source, target, m.getName(), Collections.emptyList()));
					extractStepsTo(info, parent, out);
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
					final Parameter[] mParams = m.getParameters();
					for (Parameter p : mParams) {
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

					checkCacheEntries: for (Entry<Object, Object> ent : cache.entrySet()) {
						if (ent.getValue() == astNode) {
							final Object param = ent.getKey();

							final List<ParameterValue> serializableParams = new ArrayList<>();
							if (mParams.length > 1) {
								if (!(param instanceof List<?>)) {
									System.out.println("Method " + m.getName() + " has " + mParams.length
											+ " params, but cache key isn't a list? It is: " + param);
									continue checkCacheEntries;
								}
								final List<?> argList = (ArrayList<?>) param;
								int paramIdx = 0;
								for (Object v : argList) {
									final ParameterValue decoded = CreateValue.fromInstance(info,
											mParams[paramIdx++].getType(), v);
									if (decoded == null) {
										System.out.println("Unknown parameter " + v);
										continue checkCacheEntries;
									}
									serializableParams.add(decoded);
								}
							} else {
								final ParameterValue decoded = CreateValue.fromInstance(info, mParams[0].getType(),
										param);
								if (decoded == null) {
									System.out.println("Unknown parameter " + param);
									continue checkCacheEntries;
								}
								serializableParams.add(decoded);
							}
//							final SerializableParameterType serializable = SerializableParameterType
//									.decode(param.getClass(), baseAstClazz);
							final TypeAtLoc realSource = TypeAtLoc.from(realParent, info.recoveryStrategy);
							out.add(new NodeEdge.ParameterizedNtaEdge(realSource, target, m.getName(), serializableParams));
							extractStepsTo(info, realParent, out);
							return;

//							if (serializable != null) {
//								return;
//							} else {
//								System.out.println("Found parameterized NTA edge from " + m.getName()
//										+ ", but it takes param '" + param + "' (type=" + param.getClass()
//										+ "), not sure how to encode it");
//							}

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
