package codeprober.locator;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.CreateLocator.LocatorMergeMethod;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.metaprogramming.TypeIdentifier;
import codeprober.protocol.ParameterValue;
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

	public static boolean isFirstPerfectMatchExpected(AstInfo info, AstNode src, TypeAtLoc tal, int depth,
			AstNode expected) {
		return getFirstPerfectMatch(info, src,
				info.typeIdentificationStyle.createIdentifier(info.loadAstClass, tal.type, tal.label), tal.loc.start, tal.loc.end,
				depth, expected) == expected;
	}

	private static AstNode getFirstPerfectMatch(AstInfo info, AstNode src, TypeIdentifier typeIdentifier, int startPos,
			int endPos, int depth, AstNode expected) {

		if (depth < 0) {
			return null;
		}
		if (src.underlyingAstNode == expected.underlyingAstNode) {
			return expected;
		}

		final Span nodePos;
		try {
			nodePos = src.getRecoveredSpan(info);
		} catch (InvokeProblem e) {
			System.out.println("Error while extracting node position for " + src);
			e.printStackTrace();
			return null;
		}

		if (!nodePos.covers(startPos, endPos)) {
			return null;
		}

		if (depth == 0 && startPos == nodePos.start && endPos == nodePos.end) {
			if (typeIdentifier.matchesDesiredType(src)) {
				return src;
			}
		}
		for (AstNode child : src.getChildren(info)) {
			if (child.underlyingAstNode == expected.underlyingAstNode) {
				return expected;
			}
			AstNode match = getFirstPerfectMatch(info, child, typeIdentifier, startPos, endPos, depth - 1, expected);
			if (match != null) {
				return match;
			}
		}
		return null;
	}

	private static MatchedNode bestMatchingNode(AstInfo info, AstNode astNode, TypeIdentifier typeIdentifier, int startPos,
			int endPos, int depth, boolean failOnAmbiguity) {
		return bestMatchingNode(info, astNode, typeIdentifier, startPos, endPos, depth, failOnAmbiguity, null,
				Integer.MIN_VALUE);
	}

	private static MatchedNode bestMatchingNode(AstInfo info, AstNode astNode, TypeIdentifier typeIdentifier, int startPos,
			int endPos, int depth, boolean failOnAmbiguity, AstNode ignoreTraversalOn, int failOnDepthBelowLevel) {
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

		AstNode bestNode = typeIdentifier.matchesDesiredType(astNode) ? astNode : null;
		int bestError;
		int bestDepthDiff;
		if (bestNode == null) {
			bestError = Integer.MAX_VALUE;
			bestDepthDiff = bestError;

		} else {
			bestError = Math.abs(start - startPos) + Math.abs(end - endPos);
			bestDepthDiff = Math.abs(depth);

			if (bestError == 0) {
				if (bestDepthDiff <= 0 && !failOnAmbiguity) {
					// Child nodes cannot be better than this, and we don't care about ambiguity
					// Return early
					return new MatchedNode(bestNode, bestError, bestDepthDiff);
				}
				failOnDepthBelowLevel = Math.max(failOnDepthBelowLevel, -bestDepthDiff);
			}
		}
		for (AstNode child : astNode.getChildren(info)) {
			if (ignoreTraversalOn != null && ignoreTraversalOn.underlyingAstNode == child.underlyingAstNode) {
				continue;
			}
			MatchedNode recurse = bestMatchingNode(info, child, typeIdentifier, startPos, endPos, depth - 1, failOnAmbiguity,
					ignoreTraversalOn, failOnDepthBelowLevel);
			if (recurse != null) {
				final boolean isBetterMatch;
				// Checking for better matches has three levels of precedence
				if ((bestError == 0) != (recurse.matchError == 0)) {
					// 1) Perfect position match
					isBetterMatch = recurse.matchError == 0;
				} else if (recurse.depthDiff != bestDepthDiff) {
					// 2) Better depth match
					isBetterMatch = recurse.depthDiff < bestDepthDiff;
				} else {
					// 3) Better position match
					isBetterMatch = recurse.matchError < bestError;
				}
				if (isBetterMatch) {
					bestNode = recurse.matchedNode;
					bestError = recurse.matchError;
					bestDepthDiff = recurse.depthDiff;
					if (bestError == 0) {
						failOnDepthBelowLevel = Math.max(failOnDepthBelowLevel, -bestDepthDiff);
					}
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
		try {
			final MatchedNode match = bestMatchingNode(info, sourceNode, info.typeIdentificationStyle.createIdentifier(info.loadAstClass, tal.type, tal.label),
					tal.loc.start, tal.loc.end, depth, true, ignoreTraversalOn, Integer.MIN_VALUE);
			if (ignoreTraversalOn != null) {
				// Matching anything on the same depth means that there are >=two matches ->
				// ambiguous
				return match != null; // && match.depthDiff == 0;
			}
			// Two matches would throw, only need to check for zero matches here
			return match == null;
		} catch (AmbiguousTal a) {
			return true;
		}
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
							final ParameterValue param = DecodeValue.decode(info, args.getJSONObject(j),
									new JSONArray());
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
						TypeIdentifier typeIdentifier = info.typeIdentificationStyle.createIdentifier(info.loadAstClass, tal.getString("type"), tal.optString("label"));
						final AstNode parent = matchedNode;
						MatchedNode result = bestMatchingNode(info, parent, typeIdentifier, start, end, depth, false);

						if (result == null) {
							// Sometimes the locator can shift 1 or 2 characters off,
							// especially if the document enters an invalid state while typing.
							// We can permit a tiny bit of error and try again
							result = bestMatchingNode(info, parent, typeIdentifier, start - 2, end + 2, depth, false);
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
		if (System.getProperty("DEBUG_MERGE_METHODS") != null) {
			final JSONObject cmp;
			CreateLocator.setMergeMethod(LocatorMergeMethod.PAPER_VERSION);
			try {
				cmp = CreateLocator.fromNode(info, matchedNode);
			} finally {
				CreateLocator.setMergeMethod(LocatorMergeMethod.OLD_VERSION);
			}
			System.out.println("new: " + cmp.toString(2));
			if (!matchedNodeLocator.toString().equals(cmp.toString())) {
				Thread.dumpStack();
				System.err.println("Diff locator!!");
				System.err.println("#   opt: " + matchedNodeLocator.toString(2));
				System.err.println("# paper: " + cmp.toString(2));

				CreateLocator.setMergeMethod(LocatorMergeMethod.OLD_VERSION);
				try {
					CreateLocator.fromNode(info, matchedNode);
				} finally {
					CreateLocator.setMergeMethod(LocatorMergeMethod.OLD_VERSION);
				}

				CreateLocator.setMergeMethod(LocatorMergeMethod.PAPER_VERSION);
				try {
					CreateLocator.fromNode(info, matchedNode);
				} finally {
					CreateLocator.setMergeMethod(LocatorMergeMethod.OLD_VERSION);
				}

//			System.exit(1);
			} else {
//			System.out.println("Same locator");
			}
		}
		return new ResolvedNode(matchedNode, matchPos, matchedNodeLocator);
	}
}
