package codeprober.locator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.NodeEdge.NodeEdgeType;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.ParameterValue;
import codeprober.protocol.create.CreateValue;
import codeprober.util.BenchmarkTimer;

public class CreateLocator {

//	private static final boolean useFastTrimAlgorithm = true;

	private static boolean buildFastButFragileLocator = false;

	public static void setBuildFastButFragileLocator(boolean fastAndFragile) {
		buildFastButFragileLocator = fastAndFragile;
	}

	private static class Locator {
		public final List<NodeEdge> steps;

		public Locator(List<NodeEdge> steps) {
			this.steps = steps;
		}
	}

	public static JSONObject fromNode(AstInfo info, AstNode astNode) {
		BenchmarkTimer.CREATE_LOCATOR.enter();
		try {
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
			final Span astPos = astNode.getRecoveredSpan(info);
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
		} finally {
			BenchmarkTimer.CREATE_LOCATOR.exit();
		}
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
			return new Locator(Collections.<NodeEdge>emptyList());
		}
		for (NodeEdge edge : res) {
			if (edge == null) {
				return null;
			}
		}

		Collections.reverse(res);

		if (!buildFastButFragileLocator) {
			// TODO make this only happen when encoding the main locator of a probe
			// When building locators for output bodies, TaL is not really necessary.
			int trimPos = res.size() - 1;
			boolean foundTopSubstitutableNode = false;
			while (trimPos >= 0 && !foundTopSubstitutableNode) {
				int numPotentialRemovals = 0;
				NodeEdge disambiguationTarget = res.get(trimPos);
				int disambiguationDepth = 0;
				for (int i = trimPos; i >= 0; --i) {
					++disambiguationDepth;
					final NodeEdge parentEdge = res.get(i);
//					System.out.println("Locator parentEdge target class: " + parentEdge.targetNode.underlyingAstNode.getClass().getSimpleName());
					
					if (parentEdge.targetNode.isLocatorTALRoot(info)) {
						foundTopSubstitutableNode = true;
						break;
					}
					if (parentEdge.type == NodeEdgeType.ChildIndex
							&& (parentEdge.targetNode.isNonOverlappingSibling(info) || //
									!ApplyLocator.isAmbiguousTal(info, parentEdge.sourceNode,
											disambiguationTarget.targetLoc, disambiguationDepth,
											disambiguationTarget.targetNode))) {
						++numPotentialRemovals;
						if (parentEdge.targetNode.getRawSpan(info).isMeaningful()) {
							disambiguationTarget = parentEdge;
							disambiguationDepth = 0;
						}
					} else {
						break;
					}
				}
				if (numPotentialRemovals == 0) {
					--trimPos;
					continue;
				}
				final NodeEdge top = res.get(trimPos + 1 - numPotentialRemovals);
				final NodeEdge edge = res.get(trimPos);

				res.set(trimPos, new TypeAtLocEdge(top.sourceNode, top.sourceLoc, edge.targetNode, edge.targetLoc,
						numPotentialRemovals));
				for (int i = 1; i < numPotentialRemovals; i++) {
					res.remove(trimPos - i);
				}
				trimPos -= numPotentialRemovals;

			}
		}
		
		return new Locator(res);
	}

	private static void extractStepsTo(AstInfo info, AstNode astNode, List<NodeEdge> out)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException,
			NoSuchMethodException, InvocationTargetException {
		final AstNode parent = astNode.parent();
		if (parent == null) {
			// No edge needed
			return;
		}

		final TypeAtLoc source = TypeAtLoc.from(info, parent);
		final TypeAtLoc target = TypeAtLoc.from(info, astNode);
		int childIdxCounter = 0;
		for (AstNode child : parent.getChildren(info)) {
			if (child.sharesUnderlyingNode(astNode)) {
				out.add(new ChildIndexEdge(parent, source, astNode, target, childIdxCounter));
				extractStepsTo(info, parent, out);
				return;
			}
			++childIdxCounter;
		}
		if (extractNtaEdge(info, astNode, out, target, parent)) {
			return;
		}

		if (parent.getNumChildren(info) == 0) {
			// Strange proxy node that appears in NTA+param cases
			// RealParent -> Proxy -> Value
			// In this case, 'parent' is the proxy. We want the grandparent instead
			final AstNode realParent = parent.parent();
			if (realParent != null) {
				if (extractNtaEdge(info, astNode, out, target, realParent)) {
					return;
				}
			}
		}

		out.add(null);
		System.out.println("Unknown edge " + parent + " -->" + astNode);
		System.out.println("other way: " + source + " --> " + target);
		System.out.println("Parent pretty : " + Reflect.invoke0(parent.underlyingAstNode, "prettyPrint"));
		System.out.println("child type " + astNode.getClass());
//		final Field childIndex = astNode.underlyingAstNode.getClass().getDeclaredField("childIndex");
//		childIndex.setAccessible(true);
//		System.out.println("value childIndex : " + childIndex.getInt(astNode));

		AstNode search = parent;
		while (search != null) {
			search = search.parent();
			System.out.println("Grandparent.. " + search);
		}
//		addEdge.accept("UNKNOWN EDGE");
//		if (parent != null) {
//			extractStepsTo(parent, out);
//		}
	}

	private static boolean extractNtaEdge(AstInfo info, AstNode astNode, List<NodeEdge> out, final TypeAtLoc target,
			final AstNode parent)
			throws IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException {
		for (Method m : parent.underlyingAstNode.getClass().getMethods()) {
			if (!MethodKindDetector.isNta(m)) {
				continue;
			}
			Field f;
			String guessedCacheName = m.getName();
			final Type[] genParams = m.getGenericParameterTypes();
			for (Type t : genParams) {
				guessedCacheName += "_" + convTypeNameToSignature(extractSimpleNames(t));
			}
			final String guessedProxyName = guessedCacheName + "_proxy";
			final boolean expectSingleValueCache = m.getParameterCount() == 0;
			if (expectSingleValueCache) {
				guessedCacheName += "_value";
			} else {
				guessedCacheName += "_values";
			}

			boolean isTheProxyNode = false;
			if (!expectSingleValueCache) {
				try {
					Field proxy = m.getDeclaringClass().getDeclaredField(guessedProxyName);
					proxy.setAccessible(true);
					if (proxy.get(parent.underlyingAstNode) == astNode.underlyingAstNode) {
						/**
						 * Oh dear. We are in the following situation:
						 *
						 * <pre>
						 * 		 Parent
						 * 			|`- - - .
						 * 			V       V
						 * 		  Proxy   (NTA)
						 * 			|       /
						 * 			| - - -´
						 * 			V
						 * 		   Child
						 * </pre>
						 *
						 * "astNode" is the Proxy.
						 *
						 * Normally people go from Parent to Child through the NTA. The Proxy is just
						 * some sort of implementation detail that isn't really supposed to be
						 * interacted with. But if you do Child.getParent(), you'll get it.
						 *
						 * We can construct a locator to it by finding a child in the NTA cache (any
						 * child will do) and getting parent from it.
						 */
						isTheProxyNode = true;
					}
				} catch (NoSuchFieldException e) {
					System.out.println(
							"Failed to guess values proxy name for " + m + ", thought it was " + guessedProxyName);
					e.printStackTrace();
				}
			}

			try {
				f = m.getDeclaringClass().getDeclaredField(guessedCacheName);
			} catch (NoSuchFieldException e) {
				System.out
						.println("Failed to guess values field name for " + m + ", thought it was " + guessedCacheName);

				System.out.println("reguess: " + guessedCacheName);
				System.out.println("gens: " + Arrays.toString(genParams));
				e.printStackTrace();
				continue;
			}
			f.setAccessible(true);

			if (expectSingleValueCache) {
				final Object cachedNtaValue = f.get(parent.underlyingAstNode);
				// Intentional identity comparison
				if (cachedNtaValue != astNode.underlyingAstNode) {
					continue;
				}
				out.add(new ParameterizedNtaEdge(parent, TypeAtLoc.from(info, astNode), astNode, target, m.getName(),
						Collections.<ParameterValue>emptyList()));
				extractStepsTo(info, parent, out);
				return true;
			}
//			if (m.getpar)
			@SuppressWarnings("unchecked")
			final Map<Object, Object> cache = (Map<Object, Object>) f.get(parent.underlyingAstNode);
			if (cache == null) {
				continue;
			}

			final Parameter[] mParams = m.getParameters();
			checkCacheEntries: for (Entry<Object, Object> ent : cache.entrySet()) {
				if (isTheProxyNode || ent.getValue() == astNode.underlyingAstNode) {
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
							final ParameterValue decoded = CreateValue.fromInstance(info, mParams[paramIdx].getType(),
									genParams[paramIdx], v);
							++paramIdx;
							if (decoded == null) {
								System.out.println("Unknown parameter at index " + (paramIdx - 1) + ":" + v);
								continue checkCacheEntries;
							}
							serializableParams.add(decoded);
						}
					} else {
						final ParameterValue decoded = CreateValue.fromInstance(info, mParams[0].getType(),
								genParams[0], param);
						if (decoded == null) {
							System.out.println(
									"Unknown parameter " + param + ", class name: " + param.getClass().getName());
							continue checkCacheEntries;
						}
						serializableParams.add(decoded);
					}
					final TypeAtLoc realSource = TypeAtLoc.from(info, parent);
					if (isTheProxyNode) {
						// "bounce" off the child node for the proxy-reasons listed above.
						final AstNode bounceChild = new AstNode(ent.getValue());
						final TypeAtLoc bounceChildLoc = TypeAtLoc.from(info, bounceChild);

						out.add(new ParameterizedNtaEdge(bounceChild, bounceChildLoc, astNode, target, "getParent",
								new ArrayList<>()));
						out.add(new ParameterizedNtaEdge(parent, realSource, bounceChild, bounceChildLoc, m.getName(),
								serializableParams));
					} else {
						out.add(new ParameterizedNtaEdge(parent, realSource, astNode, target, m.getName(),
								serializableParams));
					}
					extractStepsTo(info, parent, out);
					return true;

				}
			}
		}
		return false;
	}

	private static String extractSimpleNames(Type type) {
		if (type instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type;
			String build = extractSimpleNames(pt.getRawType()) + "<";
			boolean first = true;
			for (Type gen : pt.getActualTypeArguments()) {
				if (!first) {
					build += ", ";
				}
				first = false;
				build += extractSimpleNames(gen);
			}
			return build + ">";
		}
		String typestr = type.toString();
		final int lastDot = typestr.lastIndexOf('.');
		if (lastDot == -1 || lastDot == typestr.length() - 1) {
			return typestr;
		}
		typestr = typestr.substring(lastDot + 1);
		final int lastDollar = typestr.lastIndexOf('$');
		if (lastDollar == -1 || lastDollar == typestr.length() - 1) {
			return typestr;
		}
		return typestr.substring(lastDollar + 1);

	}

	// This function is stolen from JastAdd (src/jastadd/ast/NameBinding.jrag) so
	// that we can mimic the cache naming convention.
	private static String convTypeNameToSignature(String s) {
		s = s.replace('.', '_');
		s = s.replace(' ', '_');
		s = s.replace(',', '_');
		s = s.replace('<', '_');
		s = s.replace('>', '_');
		s = s.replace('[', '_');
		s = s.replace('?', '_');
		s = s.replace(']', 'a');
		return s;
	}
}
