package codeprober.locator;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.ParameterValue;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.decode.DecodeValue;
import codeprober.util.BenchmarkTimer;

public class ApplyLocator {

	public static class ResolvedNode {
		public final AstNode node;
		public final Span pos;
		public final JSONObject nodeLocator;

		public ResolvedNode(AstNode node, Span pos, JSONObject nodeLocator) {
			this.node = node;
			this.pos = pos;
			this.nodeLocator = nodeLocator;
		}

		@Override
		public String toString() {
			return "@" + pos + ":" + node.toString();
		}
	}

	private static class MatchedNode {
		public final AstNode matchedNode;
		public final int matchError;
		public final int depthDiff;

		public MatchedNode(AstNode matchedNode, int matchError, int depthDiff) {
			this.matchedNode = matchedNode;
			this.matchError = matchError;
			this.depthDiff = depthDiff;
		}
	}

	@SuppressWarnings("serial")
	private static class AmbiguousTal extends RuntimeException {
	}

	private static MatchedNode bestMatchingNode(AstInfo info, AstNode astNode, Class<?> nodeType, int startPos,
			int endPos, int depth, PositionRecoveryStrategy recoveryStrategy, boolean failOnAmbiguity) {
		return bestMatchingNode(info, astNode, nodeType, startPos, endPos, depth, recoveryStrategy, failOnAmbiguity,
				null, Integer.MIN_VALUE);
	}

	private static MatchedNode bestMatchingNode(AstInfo info, AstNode astNode, Class<?> nodeType, int startPos,
			int endPos, int depth, PositionRecoveryStrategy recoveryStrategy, boolean failOnAmbiguity,
			AstNode ignoreTraversalOn, int failOnDepthBelowLevel) {
		if (depth < failOnDepthBelowLevel) {
			return null;
		}

		final Span nodePos;
		try {
			nodePos = astNode.getRecoveredSpan(info);
		} catch (InvokeProblem e) {
			System.out.println("Error while extracting node position for " + astNode);
			e.printStackTrace();
			return null;
		}
		final int start = nodePos.start;
		final int end = nodePos.end;

		if (start != 0 && end != 0 && startPos != 0 && endPos != 0 && (start > endPos || end < startPos)) {
//			// Assume that a parent only contains children within its own bounds
			return null;
		}

		AstNode bestNode = nodeType.isInstance(astNode.underlyingAstNode) ? astNode : null;
		int bestError;
		int bestDepthDiff;
		if (bestNode == null) {
			bestError = Integer.MAX_VALUE;
			bestDepthDiff = bestError;

		} else {
			bestError = Math.abs(start - startPos) + Math.abs(end - endPos);
			bestDepthDiff = Math.abs(depth);

			if (bestDepthDiff <= 0 && !failOnAmbiguity) {
				// Child nodes cannot be better than this, and we don't care about ambiguity
				// Return early
				return new MatchedNode(bestNode, bestError, bestDepthDiff);
			}
			failOnDepthBelowLevel = Math.max(failOnDepthBelowLevel, -bestDepthDiff);
		}
		for (AstNode child : astNode.getChildren(info)) {
			if (ignoreTraversalOn != null && ignoreTraversalOn.underlyingAstNode == child.underlyingAstNode) {
				continue;
			}
			MatchedNode recurse = bestMatchingNode(info, child, nodeType, startPos, endPos, depth - 1, recoveryStrategy,
					failOnAmbiguity, ignoreTraversalOn, failOnDepthBelowLevel);
			if (recurse != null) {
				final boolean isBetterMatch = //
						// Better depth has highest priority
						recurse.depthDiff < bestDepthDiff
								// Otherwise, same depth and closer offset -> better
								|| (recurse.depthDiff == bestDepthDiff && recurse.matchError < bestError);
				if (isBetterMatch) {
					bestNode = recurse.matchedNode;
					bestError = recurse.matchError;
					bestDepthDiff = recurse.depthDiff;
					failOnDepthBelowLevel = Math.max(failOnDepthBelowLevel, -bestDepthDiff);
				} else if (recurse.depthDiff == bestDepthDiff && recurse.matchError == bestError && failOnAmbiguity) {
					throw new AmbiguousTal();
				}
			}
		}
		if (bestNode == null) {
			return null;
		}
		return new MatchedNode(bestNode, bestError, bestDepthDiff);
	}

	public static boolean isAmbiguousTal(AstInfo info, AstNode sourceNode, TypeAtLoc tal, int depth,
			AstNode ignoreTraversalOn) {
//		switch (step.getString("type")) {
//		case "nta":
//		case "child": {
//			return false;
//		}
//		case "tal": {
//			final JSONObject tal = step.getJSONObject("value");
		try {
			final MatchedNode match = bestMatchingNode(info, sourceNode,
					info.loadAstClass.apply(info.getQualifiedAstType(tal.type)), tal.loc.start, tal.loc.end, depth,
					info.recoveryStrategy, true, ignoreTraversalOn, Integer.MIN_VALUE);
			if (ignoreTraversalOn != null) {
				// Matching anything means that there are >=two matches -> ambiguous
				return match != null;
			}
			// Two matches would throw, only need to check for zero matches here
			return match == null;
		} catch (AmbiguousTal a) {
			return true;
		}
//		}
//		default: {
//			System.err.println("Unknown edge type '" + step.getString("type"));
//			return true;
//		}
//		}
	}

	public static ResolvedNode toNode(AstInfo info, JSONObject locator) {
		AstNode matchedNode = info.ast;
		Span matchPos = null;

		BenchmarkTimer.APPLY_LOCATOR.enter();
		try {

			if (matchedNode != null) {
				final List<Object> matchSequence = new ArrayList<>();
				final JSONArray steps = locator.getJSONArray("steps");
				for (int i = 0; i < steps.length(); i++) {
					matchSequence.add(matchedNode);
					final JSONObject step = steps.getJSONObject(i);
					switch (step.getString("type")) {
					case "nta": {
						final JSONObject mth = step.getJSONObject("value");
						final JSONArray args = mth.getJSONArray("args");
						final Object[] argsValues = new Object[args.length()];
						final Class<?>[] argsTypes = new Class<?>[args.length()];
						final String ntaName = mth.getString("name");
						for (int j = 0; j < args.length(); ++j) {
							final ParameterValue param = DecodeValue.decode(info, args.getJSONObject(j));
							if (param == null) {
								System.out.println("Failed decoding parameter " + i + " for NTA '" + ntaName + "'");
								return null;
							}
							argsValues[j] = param.getUnpackedValue();
							argsTypes[j] = param.paramType;
						}
						Object match = Reflect.invokeN(matchedNode.underlyingAstNode, ntaName, argsTypes, argsValues);
						matchedNode = match == null ? null : new AstNode(match);
						break;
					}
					case "tal": {
						final JSONObject tal = step.getJSONObject("value");
						final int start = tal.getInt("start");
						final int end = tal.getInt("end");
						final int depth = tal.getInt("depth");
						final Class<?> clazz = info.loadAstClass.apply(info.getQualifiedAstType(tal.getString("type")));
						final AstNode parent = matchedNode;
						MatchedNode result = bestMatchingNode(info, parent, clazz, start, end, depth,
								info.recoveryStrategy, false);

						if (result == null) {
							// Sometimes the locator can shift 1 or 2 characters off,
							// especially if the document enters an invalid state while typing.
							// We can permit a tiny bit of error and try again
							result = bestMatchingNode(info, parent, clazz, start - 2, end + 2, depth,
									info.recoveryStrategy, false);
						}
						matchedNode = result != null ? result.matchedNode : null;
						break;
					}
					case "child": {
						final int childIndex = step.getInt("value");
						if (childIndex < 0 || childIndex >= matchedNode.getNumChildren(info)) {
							return null;
						}
						matchedNode = matchedNode.getNthChild(info, childIndex);
						break;
					}
					default: {
						throw new RuntimeException("Unknown locator step '" + step.toString(0) + "'");
					}
					}
					if (matchedNode == null) {
						System.out.println("Failed matching after step " + i);
						break;
					}
				}
			}
			if (matchedNode != null) {
				try {
					matchPos = matchedNode.getRecoveredSpan(info);
				} catch (InvokeProblem e1) {
					System.out.println("Error while extracting position of matched node");
					e1.printStackTrace();
				}
			}

		} finally {
			BenchmarkTimer.APPLY_LOCATOR.exit();
		}

		JSONObject matchedNodeLocator = null;
		if (matchedNode != null && matchPos != null) {
			// Create fresh locator so this node is easier to find in the future
			matchedNodeLocator = CreateLocator.fromNode(info, matchedNode);
		}
		if (matchedNode == null || matchPos == null || matchedNodeLocator == null) {
			return null;
		}
		return new ResolvedNode(matchedNode, matchPos, matchedNodeLocator);
	}
}
