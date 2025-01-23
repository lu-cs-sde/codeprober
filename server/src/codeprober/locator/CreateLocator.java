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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.create.CreateValue;
import codeprober.protocol.data.FNStep;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.NodeLocatorStep;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.TALStep;
import codeprober.util.BenchmarkTimer;

public class CreateLocator {

//	private static final boolean useFastTrimAlgorithm = true;

	public static enum LocatorMergeMethod {
		SKIP, PAPER_VERSION;
//		OLD_VERSION;

		public static LocatorMergeMethod DEFAULT_METHOD = PAPER_VERSION;
	}

	public static class StepWithTarget {
		public final NodeLocatorStep step;
		public final AstNode source;
		public final AstNode target;

		public StepWithTarget(NodeLocatorStep step, AstNode source, AstNode target) {
			this.step = step;
			this.source = source;
			this.target = target;
		}
	}

	public static IdentityHashMap<Object, NodeLocator> identityLocatorCache = null;

	public static class Edges {
		public final int unmergedLength;
		public final List<StepWithTarget> mergedEdges;

		public Edges(int unmergedLength, List<StepWithTarget> mergedEdges) {
			this.unmergedLength = unmergedLength;
			this.mergedEdges = mergedEdges;
		}
	}

	private static LocatorMergeMethod mergeMethod = LocatorMergeMethod.DEFAULT_METHOD;

	public static void setMergeMethod(LocatorMergeMethod mth) {
		mergeMethod = mth;
	}

	public static LocatorMergeMethod getMergeMethod() {
		return mergeMethod;
	}

	public static void putNodeTypeValues(AstNode node, JSONObject dst) {
		dst.put("type", node.underlyingAstNode.getClass().getName());
		String lbl = node.getNodeLabel();
		if (lbl != null) {
			dst.put("label", lbl);
		}
	}

	public static TALStep createTALStep(AstInfo info, AstNode node, int depth) {
		final Span pos = node.getRecoveredSpan(info);
		return createTALStep(node, pos.start, pos.end, depth, node.isInsideExternalFile(info));
	}

	public static TALStep createTALStep(AstNode node, int start, int end, int depth, boolean isExternal) {
		final String type = node.underlyingAstNode.getClass().getName();
		final String lbl = node.getNodeLabel();
		return new TALStep(type, lbl, start, end, depth, isExternal);
	}

	private static List<Object> fromNodeCycleDetectorStack = new ArrayList<>();

	public static int numEncounteredUnattachedNodes = 0;

	public static NodeLocator fromNode(AstInfo info, AstNode astNode) {
		if (identityLocatorCache != null) {
			if (identityLocatorCache.containsKey(astNode.underlyingAstNode)) {
				return identityLocatorCache.get(astNode.underlyingAstNode);
			}
		}
		final Edges edges = getEdgesTo(info, astNode);
		if (edges == null) {
			if (identityLocatorCache != null) {
				identityLocatorCache.put(astNode.underlyingAstNode, null);
			}
			return null;
		}

		final Span astPos = astNode.getRecoveredSpan(info);
		final NodeLocator ret = new NodeLocator(
				createTALStep(astNode, astPos.start, astPos.end, edges.unmergedLength, astNode.isInsideExternalFile(info)),
				edges.mergedEdges.stream().map(x -> x.step).collect(Collectors.toList()));
		if (identityLocatorCache != null) {
			identityLocatorCache.put(astNode.underlyingAstNode, ret);
		}
		return ret;
	}

	public static Edges getEdgesTo(AstInfo info, AstNode astNode) {
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
				Edges edges = doGetEdgesTo(info, astNode);
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

	private static Edges doGetEdgesTo(AstInfo info, AstNode astNode) {
		final List<StepWithTarget> naive = new ArrayList<>();

		final Field childIndexField = info.getChildIndexField();
		try {
			extractStepsTo(info, astNode, childIndexField, naive);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
				| NoSuchMethodException | InvocationTargetException e) {
			System.out.println("Error when resolving path to " + astNode + " from root");
			e.printStackTrace();
			return null;
		}
		if (naive.isEmpty()) {
			// Root node.. right?
			if (astNode.underlyingAstNode != info.ast.underlyingAstNode) {
				System.err.println("Tried creating locator to node without parent which is not the AST root:"  + astNode);
				++numEncounteredUnattachedNodes;
				return null;
			}
			return new Edges(0, naive);
		}
		// If something failed, the last entry is null
		if (naive.get(naive.size() - 1) == null) {
			return null;
		}
//		for (StepWithTarget edge : naive) {
//			if (edge == null) {
//				return null;
//			}
//		}
		Collections.reverse(naive);
		if (naive.get(0).source.underlyingAstNode != info.ast.underlyingAstNode) {
			System.err.println("Tried creating locator to node whose descendands does not include the AST root:"  + astNode);
			++numEncounteredUnattachedNodes;
			return null;
		}

		switch (mergeMethod) {

		case SKIP: {
			// No merge needed
			break;
		}

		case PAPER_VERSION: {
			// Functional, non-optimized version
			final BiFunction<AstNode, AstNode, Integer> distance = (src, dst) -> {
				int depth = 0;
				while (src.underlyingAstNode != dst.underlyingAstNode) {
					dst = dst.parent();
					++depth;
				}
				return depth;
			};
			final BiFunction<AstNode, AstNode, StepWithTarget> createTAL = (src, dst) -> {
				final Span pos = dst.getRecoveredSpan(info);
				return new StepWithTarget( //
						NodeLocatorStep.fromTal(createTALStep(dst, pos.start, pos.end, distance.apply(src, dst),
								dst.isInsideExternalFile(info))), //
						src, dst);
			};

			final BiPredicate<AstNode, AstNode> canUseTal = (src, dst) -> ApplyLocator.isFirstPerfectMatchExpected(info,
					src.parent(), TypeAtLoc.from(info, dst), distance.apply(src.parent(), dst), src);

			AstNode src = astNode;
			AstNode dst = astNode;
			final List<StepWithTarget> ret = new ArrayList<>();

			// 'foundTALRoot' differs from naive paper version.
			// Optimizes performance greatly, especially in a multi-file scenario.
			// Included to make it behave more like the 'optimized' version.
			// Remove to simulate paper algorithm.
			boolean foundTALRoot = astNode.isLocatorTALRoot(info);

			final int endIdx = naive.size() - 1;
			for (int i = endIdx; i >= 0; --i) {
				final StepWithTarget swt = naive.get(i);
				final NodeLocatorStep step = swt.step;
				if (!foundTALRoot) {
					foundTALRoot = src.isLocatorTALRoot(info);
				}

				if (src != dst && (step.isNta() || foundTALRoot || !canUseTal.test(src, dst))) {
					ret.add(createTAL.apply(src, dst));
					dst = src;
				}

				if (step.isNta() || foundTALRoot || !canUseTal.test(src, dst)) {
					ret.add(swt);
					dst = swt.source;
				} else {
					// Let it grow
				}
				src = swt.source;
			}
			if (src != dst) {
				ret.add(createTAL.apply(src, dst));
			}
			Collections.reverse(ret);
			return new Edges(naive.size(), ret);
		}

//		case OLD_VERSION: {
//
//			int trimPos = naive.size() - 1;
//			boolean foundTopSubstitutableNode = false;
//			while (trimPos >= 0 && !foundTopSubstitutableNode) {
//				int numPotentialRemovals = 0;
//				NodeEdge disambiguationTarget = naive.get(trimPos);
//				int disambiguationDepth = 0;
//				for (int i = trimPos; i >= 0; --i) {
//					++disambiguationDepth;
//					final NodeEdge parentEdge = naive.get(i);
//
//					if (parentEdge.targetNode.isLocatorTALRoot(info)) {
//						foundTopSubstitutableNode = true;
//						break;
//					}
//
//					if (parentEdge.type == NodeEdgeType.ChildIndex
//							&& (parentEdge.targetNode.isNonOverlappingSibling(info) || //
//									!ApplyLocator.isAmbiguousTal(info, parentEdge.sourceNode,
//											disambiguationTarget.targetLoc, disambiguationDepth,
//											disambiguationTarget.targetNode))) {
//						++numPotentialRemovals;
//						if (parentEdge.targetNode.getRawSpan(info).isMeaningful()) {
//							disambiguationTarget = parentEdge;
//							disambiguationDepth = 0;
//						}
//					} else {
//						break;
//					}
//				}
//				if (numPotentialRemovals == 0) {
//					--trimPos;
//					continue;
//				}
//				final NodeEdge top = naive.get(trimPos + 1 - numPotentialRemovals);
//				final NodeEdge edge = naive.get(trimPos);
//
//				naive.set(trimPos, new TypeAtLocEdge(top.sourceNode, top.sourceLoc, edge.targetNode, edge.targetLoc,
//						numPotentialRemovals, edge.targetNode.isInsideExternalFile(info)));
//				for (int i = 1; i < numPotentialRemovals; i++) {
//					naive.remove(trimPos - i);
//				}
//				trimPos -= numPotentialRemovals;
//
//			}
//			break;
//		}

		}

		return new Edges(naive.size(), naive);
	}

	private static void extractStepsTo(AstInfo info, AstNode astNode, Field childIndexField, List<StepWithTarget> out)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException,
			NoSuchMethodException, InvocationTargetException {
		final AstNode parent = astNode.parent();
		if (parent == null) {
			// No edge needed
			return;
		}

		final int numChildren = parent.getNumChildren(info);
		int guessedChildIndex = -1;
		if (childIndexField != null) {
			try {
				guessedChildIndex = ((Integer) childIndexField.get(astNode.underlyingAstNode)).intValue();
			} catch (ClassCastException | IllegalAccessException e) {
				System.err.println("Error accessing childIndex field");
			}
		}
		if (guessedChildIndex >= 0 && guessedChildIndex < numChildren) {
			final AstNode child = parent.getNthChild(info, guessedChildIndex);
			if (child.sharesUnderlyingNode(astNode)) {
				out.add(new StepWithTarget(NodeLocatorStep.fromChild(guessedChildIndex), parent, astNode));
				extractStepsTo(info, parent, childIndexField, out);
				return;
			}
		}

		for (int childIdx = 0; childIdx < numChildren; ++childIdx) {
			final AstNode child = parent.getNthChild(info, childIdx);
			if (child.sharesUnderlyingNode(astNode)) {
				out.add(new StepWithTarget(NodeLocatorStep.fromChild(childIdx), parent, astNode));
				extractStepsTo(info, parent, childIndexField, out);
				return;
			}
		}

		if (extractNtaEdge(info, astNode, childIndexField, out, parent)) {
			return;
		}

		if (numChildren == 0) {
			// Strange proxy node that appears in NTA+param cases
			// RealParent -> Proxy -> Value
			// In this case, 'parent' is the proxy. We want the grandparent instead
			final AstNode realParent = parent.parent();
			if (realParent != null) {
				if (extractNtaEdge(info, astNode, childIndexField, out, realParent)) {
					return;
				}
			}
		}

		final String getChildNoTransformMthName = "getChildNoTransform";
		if (info.hasOverride1(parent.underlyingAstNode.getClass(), getChildNoTransformMthName, Integer.TYPE)) {
			// 'child' may be the pre-transform child of parent.
			if (guessedChildIndex >= 0 && guessedChildIndex < numChildren) {
				final Object rawChild = Reflect.invokeN(parent.underlyingAstNode, getChildNoTransformMthName,
						new Class<?>[] { Integer.TYPE }, new Object[] { guessedChildIndex });
				if (rawChild == astNode.underlyingAstNode) {
					out.add(new StepWithTarget(
							NodeLocatorStep.fromNta(new FNStep(new Property(getChildNoTransformMthName,
									Arrays.asList(PropertyArg.fromInteger(guessedChildIndex))))),
							parent, astNode));
					extractStepsTo(info, parent, childIndexField, out);
					return;
				}
			}

			for (int childIdx = 0; childIdx < numChildren; ++childIdx) {
				final Object rawChild = Reflect.invokeN(parent.underlyingAstNode, getChildNoTransformMthName,
						new Class<?>[] { Integer.TYPE }, new Object[] { childIdx });
				if (rawChild == astNode.underlyingAstNode) {
					out.add(new StepWithTarget(
							NodeLocatorStep.fromNta(new FNStep(new Property(getChildNoTransformMthName,
									Arrays.asList(PropertyArg.fromInteger(childIdx))))),
							parent, astNode));
					extractStepsTo(info, parent, childIndexField, out);
					return;
				}
			}
		}

		out.add(null);
		final TypeAtLoc source = TypeAtLoc.from(info, parent);
		final TypeAtLoc target = TypeAtLoc.from(info, astNode);
		System.out.println("Unknown edge " + parent + " -->" + astNode);
		System.out.println("other way: " + source + " --> " + target);
//		System.out.println("Parent pretty : " + Reflect.invoke0(parent.underlyingAstNode, "prettyPrint"));
		System.out.println("child type " + astNode.getClass());
//		final Field childIndex = astNode.underlyingAstNode.getClass().getDeclaredField("childIndex");
//		childIndex.setAccessible(true);
//		System.out.println("value childIndex : " + childIndex.getInt(astNode));

		AstNode search = parent;
		while (search != null) {
			search = search.parent();
			System.out.println("Grandparent.. " + search);
		}

//
//		if (extractNtaEdge(info, astNode, childIndexField, out, parent)) {
//			return;
//		}
//		addEdge.accept("UNKNOWN EDGE");
//		if (parent != null) {
//			extractStepsTo(parent, out);
//		}
	}

	private static boolean extractNtaEdge(AstInfo info, AstNode astNode, Field childIndexField,
			List<StepWithTarget> out, final AstNode parent)
			throws IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException {

		BenchmarkTimer.CREATE_LOCATOR_NTA_STEP.enter();
		for (Method m : info.getNtaMethods(parent.underlyingAstNode.getClass())) {
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

			Field f;
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
				out.add(new StepWithTarget(
						NodeLocatorStep.fromNta(
								new FNStep(new Property(m.getName(), Collections.<PropertyArg>emptyList(), null))),
						parent, astNode));
				BenchmarkTimer.CREATE_LOCATOR_NTA_STEP.exit();
				extractStepsTo(info, parent, childIndexField, out);
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

					final List<PropertyArg> serializableParams = new ArrayList<>();
					if (mParams.length > 1) {
						if (!(param instanceof List<?>)) {
							System.out.println("Method " + m.getName() + " has " + mParams.length
									+ " params, but cache key isn't a list? It is: " + param);
							continue checkCacheEntries;
						}
						final List<?> argList = (List<?>) param;
						int paramIdx = 0;
						for (Object v : argList) {
							final PropertyArg decoded = CreateValue.fromInstance(info, mParams[paramIdx].getType(),
									genParams[paramIdx], v);
							++paramIdx;
							if (decoded == null) {
								System.out.println("Unknown parameter at index " + (paramIdx - 1) + ":" + v);
								continue checkCacheEntries;
							}
							serializableParams.add(decoded);
						}
					} else {
						final PropertyArg decoded = CreateValue.fromInstance(info, mParams[0].getType(), genParams[0],
								param);
						if (decoded == null) {
							System.out.println(
									"Unknown parameter " + param + ", class name: " + param.getClass().getName());
							continue checkCacheEntries;
						}
						serializableParams.add(decoded);
					}
//					if (m.getName().equals("lookupParMethodDecl")) {
//						if (parent.getClass().getSimpleName().contains("MethodDecl")) {
//							MethodDecl md = (MethodDecl)parent.underlyingAstNode;
//							if (md.name().contains("hashCode")) {
//								System.out.println("!! time");
//							}
//						}
//					}
//					final TypeAtLoc realSource = TypeAtLoc.from(info, parent);
					if (isTheProxyNode) {
						// "bounce" off the child node for the proxy-reasons listed above.
						final AstNode bounceChild = new AstNode(ent.getValue());

						out.add(new StepWithTarget(
								NodeLocatorStep.fromNta(new FNStep(
										new Property("getParent", Collections.<PropertyArg>emptyList(), null))),
								bounceChild, astNode));
						out.add(new StepWithTarget(
								NodeLocatorStep
										.fromNta(new FNStep(new Property(m.getName(), serializableParams, null))),
								parent, bounceChild));
					} else {
						out.add(new StepWithTarget(
								NodeLocatorStep
										.fromNta(new FNStep(new Property(m.getName(), serializableParams, null))),
								parent, astNode));
					}
					BenchmarkTimer.CREATE_LOCATOR_NTA_STEP.exit();
					extractStepsTo(info, parent, childIndexField, out);
					return true;

				}
			}
		}
		BenchmarkTimer.CREATE_LOCATOR_NTA_STEP.exit();
		return false;
	}

	public static String extractSimpleNames(Type type) {
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
	public static String convTypeNameToSignature(String s) {
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
