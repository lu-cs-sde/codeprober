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
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.NodeEdge.NodeEdgeType;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.ParameterValue;
import codeprober.protocol.create.CreateValue;
import codeprober.util.BenchmarkTimer;

public class CreateLocator {

//	private static final boolean useFastTrimAlgorithm = true;

	public static enum LocatorMergeMethod {
		SKIP, PAPER_VERSION, OLD_VERSION;

		public static LocatorMergeMethod DEFAULT_METHOD = PAPER_VERSION;
	}

	private static LocatorMergeMethod mergeMethod = LocatorMergeMethod.DEFAULT_METHOD;

	public static void setMergeMethod(LocatorMergeMethod mth) {
		mergeMethod = mth;
	}

//	private static class Locator {
//		public final List<NodeEdge> steps;
//
//		public Locator(List<NodeEdge> steps) {
//			this.steps = steps;
//		}
//	}

	public static void putNodeTypeName(JSONObject dst, Object node) {
		dst.put("type", node.getClass().getName());
		try {
			final String lbl = (String) Reflect.invoke0(node, "cpr_nodeLabel");
			dst.put("label", lbl);
		} catch (InvokeProblem | ClassCastException e) {
			// Ignore
		}
	}

	private static List<Object> fromNodeCycleDetectorStack = new ArrayList<>();

	public static JSONObject fromNode(AstInfo info, AstNode astNode) {
		final List<NodeEdge> edges = getEdgesTo(info, astNode);
		if (edges == null) {
			return null;
		}

		final JSONObject robustResult = new JSONObject();
		final Span astPos = astNode.getRecoveredSpan(info);
		robustResult.put("start", astPos.start);
		robustResult.put("end", astPos.end);
		if (astNode.isInsideExternalFile(info)) {
			robustResult.put("external", true);
		}
		putNodeTypeName(robustResult, astNode.underlyingAstNode);

		final JSONArray steps = new JSONArray();
		for (NodeEdge step : edges) {
			steps.put(step.toJson());
		}

		final JSONObject robust = new JSONObject();
		robust.put("result", robustResult);
		robust.put("steps", steps);
		return robust;
	}

	public static List<NodeEdge> getEdgesTo(AstInfo info, AstNode astNode) {
		if (fromNodeCycleDetectorStack.contains(astNode.underlyingAstNode)) {
			System.err.println("Illegal cycle in AST parent chain");
			for (Object n : fromNodeCycleDetectorStack) {
				System.err.print(n.getClass().getSimpleName() + "@" + System.identityHashCode(n) + " -> ");
			}
			System.err.println(astNode.underlyingAstNode.getClass().getSimpleName() + "@"
					+ System.identityHashCode(astNode.underlyingAstNode));

			System.err.println("In JastAdd, this can happen for parameterized nta's that use eachother as parameters.");
			System.err.println("For example, if working with lambda calculus you might have:");
			System.err.println(" syn nta Number Program.zero() = new Zero();");
			System.err.println(" syn nta Number Program.successor(Number n) = new Successor(n);");
			System.err.println(" syn Number Program.one() = successor(zero());");
			System.err.println("If you create a probe for one(), we need to create a locator for the result.");
			System.err.println(
					"This requires us to located successor(zero()), which in turns requires us to locate zero().");
			System.err.println(
					"However, after running successor(), the parent of zero() is changed from Program to Successor.");
			System.err.println("..so to locate zero(), we need to locate its parent, which  is successor(zero())..");
			System.err.println("..but that can only be located if we can locate zero(), etc, etc, etc.");
			System.err.println("Changing the parent violates the immutability of the tree,");
			System.err.println("and this in turn breaks some assumptions that CodeProber makes.");
			System.err.println("The recommended solution here is to clone the parameters to your nta. I.e:");
			System.err.println(" syn nta Number Program.successor(Number n) = new Successor(n.treeCopyNoTransform());");
			System.err.println(
					"Another, less recommended solution is to make the field an intra-AST token reference. I.e:");
			System.err.println(
					" Successor : Number ::= <Num:Number>; // The '<' and '>' changes Num from a normal field to a token reference");
			throw new RuntimeException("Failed creating locator");
		}

		fromNodeCycleDetectorStack.add(astNode.underlyingAstNode);
		BenchmarkTimer.CREATE_LOCATOR.enter();
		try {

			try {
				List<NodeEdge> edges = doGetEdgesTo(info, astNode);
				if (edges == null) {
					System.out.println("Failed creating locator for " + astNode);
				}
				return edges;
			} catch (RuntimeException e) {
				System.out.println("Failed to extract locator for " + astNode);
				e.printStackTrace();
				return null;
			}
		} finally {
			BenchmarkTimer.CREATE_LOCATOR.exit();
			fromNodeCycleDetectorStack.remove(fromNodeCycleDetectorStack.size() - 1);
		}
	}

	private static List<NodeEdge> doGetEdgesTo(AstInfo info, AstNode astNode) {
		final List<NodeEdge> naive = new ArrayList<>();

		try {
			extractStepsTo(info, astNode, naive);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
				| NoSuchMethodException | InvocationTargetException e) {
			System.out.println("Error when resolving path to " + astNode + " from root");
			e.printStackTrace();
			return null;
		}

		if (naive.isEmpty()) {
			// Root node!
			return Collections.<NodeEdge>emptyList();
		}
		for (NodeEdge edge : naive) {
			if (edge == null) {
				return null;
			}
		}

		Collections.reverse(naive);

		switch (mergeMethod) {

		case SKIP: {
			// No merge needed
			break;
		}

		case PAPER_VERSION: {
			// Functional, non-optimized version
			final BiFunction<AstNode, AstNode, Integer> distance = (src, dst) -> {
				int depth = 0;
				while (src != dst) {
					dst = dst.parent();
					++depth;
				}
				return depth;
			};
			final BiFunction<AstNode, AstNode, TypeAtLocEdge> createTAL = (src, dst) -> {
				return new TypeAtLocEdge(info, src, dst, distance.apply(src, dst), dst.isInsideExternalFile(info));

			};

			final BiPredicate<AstNode, AstNode> canUseTal = (src, dst) -> ApplyLocator.isFirstPerfectMatchExpected(info,
					src.parent(), TypeAtLoc.from(info, dst), distance.apply(src.parent(), dst), src);

			AstNode src = astNode;
			AstNode dst = astNode;
			final List<NodeEdge> ret = new ArrayList<>();

			// 'foundTALRoot' differs from naive paper version.
			// Optimizes performance greatly, especially in a multi-file scenario.
			// Included to make it behave more like the 'optimized' version.
			// Remove to simulate paper algorithm.
			boolean foundTALRoot = astNode.isLocatorTALRoot(info);

			for (int i = naive.size() - 1; i >= 0; --i) {
				final NodeEdge step = naive.get(i);
				if (!foundTALRoot) {
					foundTALRoot = src.isLocatorTALRoot(info);
				}

				if (src != dst && (step.type == NodeEdgeType.FN || foundTALRoot || !canUseTal.test(src, dst))) {
					ret.add(createTAL.apply(src, dst));
					dst = src;
				}

				if (step.type == NodeEdgeType.FN || foundTALRoot || !canUseTal.test(src, dst)) {
					ret.add(step);
					dst = src.parent();
				} else {
					// Let it grow
				}
				src = src.parent();
			}
			if (src != dst) {
				ret.add(createTAL.apply(src, dst));
			}
			Collections.reverse(ret);
			return ret;
		}

		case OLD_VERSION: {

			int trimPos = naive.size() - 1;
			boolean foundTopSubstitutableNode = false;
			while (trimPos >= 0 && !foundTopSubstitutableNode) {
				int numPotentialRemovals = 0;
				NodeEdge disambiguationTarget = naive.get(trimPos);
				int disambiguationDepth = 0;
				for (int i = trimPos; i >= 0; --i) {
					++disambiguationDepth;
					final NodeEdge parentEdge = naive.get(i);

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
				final NodeEdge top = naive.get(trimPos + 1 - numPotentialRemovals);
				final NodeEdge edge = naive.get(trimPos);

				naive.set(trimPos, new TypeAtLocEdge(top.sourceNode, top.sourceLoc, edge.targetNode, edge.targetLoc,
						numPotentialRemovals, edge.targetNode.isInsideExternalFile(info)));
				for (int i = 1; i < numPotentialRemovals; i++) {
					naive.remove(trimPos - i);
				}
				trimPos -= numPotentialRemovals;

			}
			break;
		}

		}

		return naive;
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
