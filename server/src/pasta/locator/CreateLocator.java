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
import pasta.ast.AstNode;
import pasta.locator.NodeEdge.NodeEdgeType;
import pasta.metaprogramming.Reflect;
import pasta.protocol.ParameterValue;
import pasta.protocol.create.CreateValue;

public class CreateLocator {

	private static class Locator {
		public final List<NodeEdge> steps;

		public Locator(List<NodeEdge> steps) {
			this.steps = steps;
		}
	}

	public static JSONObject fromNode(AstInfo info, AstNode astNode) {
		// TODO add position recovery things here(?)
		final Locator id;

		try {
			id = extract(info, astNode);
			if (id == null) {
				System.out.println("Failed creating locator for " + astNode);
				return null;
			}
		} catch (RuntimeException e) {
			System.out.println("Failed to extract locator for " + astNode);
			e.printStackTrace();
			return null;
		}

//		final JSONObject robustRoot = new JSONObject();
//		robustRoot.put("start", id.root.loc.start);
//		robustRoot.put("end", id.root.loc.end);
//		robustRoot.put("type", id.root.type);

		final JSONObject robustResult = new JSONObject();
		final Span astPos = Span.extractPosition(info, astNode);
		robustResult.put("start", astPos.start);
		robustResult.put("end", astPos.end);
		robustResult.put("type", astNode.underlyingAstNode.getClass().getSimpleName());

		final JSONArray steps = new JSONArray();
		for (NodeEdge step : id.steps) {
			steps.put(step.toJson());
		}

		final JSONObject robust = new JSONObject();
//		robust.put("root", robustRoot);
		robust.put("result", robustResult);
		robust.put("steps", steps);
//		System.out.println("Locator: " + robust.toString(2));
		return robust;
	}

	private static Locator extract(AstInfo info, AstNode astNode) {
		final List<NodeEdge> res = new ArrayList<>();

		try {
			extractStepsTo(info, astNode, res);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
				| NoSuchMethodException | InvocationTargetException e) {
			System.out.println("Error when resolving path to " + astNode + " from root");
			e.printStackTrace();
			return null;
		}

		if (res.isEmpty()) {
			// Root node!
			return new Locator(Collections.emptyList());
		}
		for (NodeEdge edge : res) {
			if (edge == null) {
				return null;
			}
		}

		Collections.reverse(res);
//		TypeAtLoc root = res.get(0).sourceLoc;
//
//		for (int i = 1; i < res.size(); i++) {
//			NodeEdge e = res.get(i);
//			if (e.type)
//			if (e.type == NodeEdgeType.TypeAtLoc) {
//				root = e.targetLoc;
//				res.remove(i);
//				--i;
//			} else {
//				// Assume the following graph (left-to-right):
//				// A tal B nta C tal D tal E nta F tal G
//				// It would be safe to remove quite a lot, down do:
//				// B nta C tal E nta F tal G
//				// This loop prunes after non-removable nodes, e.g 'C tal D'
//				while (i < res.size() - 2 && res.get(i + 1).canBeCollapsed() && res.get(i + 2).canBeCollapsed()) {
//					res.remove(i + 1);
//				}
//				break;
//			}
//		}

		return new Locator(res);
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

	private static void extractStepsTo(AstInfo info, AstNode astNode, List<NodeEdge> out)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException,
			NoSuchMethodException, InvocationTargetException {
//		final Object parent;
//		try {
//			parent = Reflect.getParent(astNode);
//		} catch (NoSuchMethodException | InvocationTargetException e) {
//			e.printStackTrace();
//			throw new RuntimeException(e);
//		}

		final AstNode parent = astNode.parent();
		if (parent == null) {
			// No edge
			return;
		}

		final TypeAtLoc source = TypeAtLoc.from(info, parent);
		final TypeAtLoc target = TypeAtLoc.from(info, astNode);
		int childIdxCounter = 0;
//		int foundTargetIndex = -1;
//		boolean ambiguousTarget = false;
		for (AstNode child : parent.getChildren()) {
			if (child.sharesUnderlyingNode(astNode)) {
				out.add(new NodeEdge.ChildIndexEdge(parent, source, astNode, target, childIdxCounter));
				final int parentPos = out.size();
				extractStepsTo(info, parent, out);

				// TODO rewrite to handle "special case" that parent is the root of the AST,
				// because in that case the "while (parentPos < out.size)" never hits.

				if (target.loc.isMeaningful()) {
					// We might be able to replace the ChildIndex edge with a TypeAtLoc.
					// It depends on the nodes that come before us.
					while (parentPos < out.size()) {
						final NodeEdge parentEdge = out.get(parentPos);
						if (parentEdge.type == NodeEdgeType.NTA) {
							final NodeEdge candidateTal = new NodeEdge.TypeAtLocEdge(parentEdge.targetNode,
									parentEdge.targetLoc, astNode, target);
							if (ApplyLocator.isAmbiguousStep(info, parentEdge.targetNode, candidateTal.toJson())) {
								break;
							}
							// Non-ambiguous! Previous step is still necessary, but we can replace our own
							// with a TAL
							out.set(parentPos - 1, candidateTal);
							break;
						} else {

							final NodeEdge candidateTal = new NodeEdge.TypeAtLocEdge(parentEdge.sourceNode,
									parentEdge.sourceLoc, astNode, target);
							if (ApplyLocator.isAmbiguousStep(info, parentEdge.sourceNode, candidateTal.toJson())) {
								break;
							}
							// Non-ambiguous! Previous step is not necessary, and our step can be replaced
							// with a TAL
							out.set(parentPos - 1, candidateTal);
							out.remove(parentPos);
						}
					}

					// Special case: handle us being immediately below the root of the ast.
					if (parentPos == out.size()) {
						final NodeEdge candidateTal = new NodeEdge.TypeAtLocEdge(info.ast,
								TypeAtLoc.from(info, info.ast), astNode, target);
						if (!ApplyLocator.isAmbiguousStep(info, info.ast, candidateTal.toJson())) {
							out.set(parentPos - 1, candidateTal);
						}
					}
				}
				return;
//				foundTargetIndex = childIdxCounter;
//			} else {
//				ambiguousTarget |= target.equals(TypeAtLoc.from(child, info.recoveryStrategy));
			}
			++childIdxCounter;
		}
		for (Method m : parent.underlyingAstNode.getClass().getMethods()) {
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
				final Object cachedNtaValue = field.get(parent.underlyingAstNode);
				// Intentional identity comparison
				if (cachedNtaValue == astNode.underlyingAstNode) {
					out.add(new NodeEdge.ParameterizedNtaEdge(parent, source, astNode, target, m.getName(),
							Collections.emptyList()));
					extractStepsTo(info, parent, out);

					return;
				}
			}
		}

		if (parent.getNumChildren() == 0) {
			// Strange proxy node that appears in NTA+param cases
			// RealParent -> Proxy -> Value
			// In this case, 'parent' is the proxy. We want the grandparent instead
			final AstNode realParent = parent.parent();
			if (realParent != null) {
				for (Method m : realParent.underlyingAstNode.getClass().getMethods()) {
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
					final Map<Object, Object> cache = (Map<Object, Object>) f.get(realParent.underlyingAstNode);
					if (cache == null) {
						continue;
					}

					checkCacheEntries: for (Entry<Object, Object> ent : cache.entrySet()) {
						if (ent.getValue() == astNode.underlyingAstNode) {
							final Object param = ent.getKey();

							final List<ParameterValue> serializableParams = new ArrayList<>();
							if (mParams.length > 1) {
								if (!(param instanceof List<?>)) {
									System.out.println("Method " + m.getName() + " has " + mParams.length
											+ " params, but cache key isn't a list? It is: " + param);
									continue checkCacheEntries;
								}
								final List<?> argList = (List<?>) param;
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
							final TypeAtLoc realSource = TypeAtLoc.from(info, realParent);
							out.add(new NodeEdge.ParameterizedNtaEdge(realParent, realSource, astNode, target,
									m.getName(), serializableParams));
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
		System.out.println("Parent pretty : " + Reflect.invoke0(parent.underlyingAstNode, "prettyPrint"));
		System.out.println("child type " + astNode.getClass());
		final Field childIndex = astNode.underlyingAstNode.getClass().getDeclaredField("childIndex");
		childIndex.setAccessible(true);
		System.out.println("value childIndex : " + childIndex.getInt(astNode));

		AstNode search = parent;
		while (search != null) {
			search = search.parent();
			System.out.println("Grandparent.. " + search);
		}
		out.add(null);
//		addEdge.accept("UNKNOWN EDGE");
//		if (parent != null) {
//			extractStepsTo(parent, out);
//		}
	}
}
