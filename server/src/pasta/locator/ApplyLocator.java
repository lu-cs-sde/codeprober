package pasta.locator;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import pasta.AstInfo;
import pasta.ast.AstNode;
import pasta.metaprogramming.InvokeProblem;
import pasta.metaprogramming.Reflect;
import pasta.protocol.ParameterValue;
import pasta.protocol.PositionRecoveryStrategy;
import pasta.protocol.decode.DecodeValue;
import pasta.util.BenchmarkTimer;

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

	@SuppressWarnings("serial")
	private static class AmbiguousTal extends RuntimeException {
	}

	private static AstNode bestMatchingNode(AstInfo info, AstNode astNode, Class<?> nodeType, int startPos, int endPos,
			PositionRecoveryStrategy recoveryStrategy, boolean failOnAmbiguity) {
		return bestMatchingNode(info, astNode, nodeType, startPos, endPos, recoveryStrategy, failOnAmbiguity, null);
	}

	private static AstNode bestMatchingNode(AstInfo info, AstNode astNode, Class<?> nodeType, int startPos, int endPos,
			PositionRecoveryStrategy recoveryStrategy, boolean failOnAmbiguity, AstNode ignoreTraversalOn) {
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

		if (start != 0 && end != 0 && (start > endPos || end < startPos)) {
//			// Assume that a parent only contains children within its own bounds
			return null;
		}

		AstNode bestNode = nodeType.isInstance(astNode.underlyingAstNode) ? astNode : null;
		int bestError = bestNode != null ? (Math.abs(start - startPos) + Math.abs(end - endPos)) : Integer.MAX_VALUE;
		for (AstNode child : astNode.getChildren()) {
			if (ignoreTraversalOn != null && ignoreTraversalOn.underlyingAstNode == child.underlyingAstNode) {
				continue;
			}
			AstNode recurse = bestMatchingNode(info, child, nodeType, startPos, endPos, recoveryStrategy,
					failOnAmbiguity, ignoreTraversalOn);
			if (recurse != null) {
				final Span recursePos;
				try {
					recursePos = recurse.getRecoveredSpan(info);
					final int recurseError = Math.abs(recursePos.start - startPos) + Math.abs(recursePos.end - endPos);
					if (recurseError < bestError) {
						bestNode = recurse;
						bestError = recurseError;
					} else if (recurseError == bestError && failOnAmbiguity) {
						throw new AmbiguousTal();
					}
				} catch (InvokeProblem e) {
					System.out.println("Error while extracting child node position");
					e.printStackTrace();
				}
			}
		}
		return bestNode;
	}

	public static boolean isAmbiguousTal(AstInfo info, AstNode sourceNode, TypeAtLoc tal, AstNode ignoreTraversalOn) {
//		switch (step.getString("type")) {
//		case "nta":
//		case "child": {
//			return false;
//		}
//		case "tal": {
//			final JSONObject tal = step.getJSONObject("value");
		try {
			final AstNode match = bestMatchingNode(info, sourceNode,
					info.loadAstClass.apply(info.getQualifiedAstType(tal.type)), tal.loc.start, tal.loc.end,
					info.recoveryStrategy, true, ignoreTraversalOn);
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
						matchedNode = bestMatchingNode(info, matchedNode,
								info.loadAstClass.apply(info.getQualifiedAstType(tal.getString("type"))),
								tal.getInt("start"), tal.getInt("end"), info.recoveryStrategy, false);
						break;
					}
					case "child": {
						final int childIndex = step.getInt("value");
						matchedNode = matchedNode.getNthChild(childIndex);
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
