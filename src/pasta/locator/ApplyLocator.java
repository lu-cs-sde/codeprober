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
		final Span nodePos;
		try {
			nodePos = Span.extractPosition(info, astNode);
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
			AstNode recurse = bestMatchingNode(info, child, nodeType, startPos, endPos, recoveryStrategy, failOnAmbiguity);
			if (recurse != null) {
				final Span recursePos;
				try {
					recursePos = Span.extractPosition(info, recurse);
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

	public static boolean isAmbiguousStep(AstInfo info, AstNode sourceNode, JSONObject step) {
		switch (step.getString("type")) {
		case "nta":
		case "child": {
			return false;
		}
		case "tal": {
			final JSONObject tal = step.getJSONObject("value");
			try {
				return bestMatchingNode(info, sourceNode,
						info.loadAstClass.apply(info.basAstClazz.getPackage().getName() + "." + tal.getString("type")),
						tal.getInt("start"), tal.getInt("end"), info.recoveryStrategy, true) == null;
			} catch (AmbiguousTal a) {
				return true;
			}
		}
		default: {
			System.err.println("Unknown edge type '" + step.getString("type"));
			return true;
		}
		}
	}

	public static ResolvedNode toNode(AstInfo info, JSONObject locator) {

//		final String rootNodeType = locator.getJSONObject("root").getString("type");
//		final int rootStart = locator.getJSONObject("root").getInt("start");
//		final int rootEnd = locator.getJSONObject("root").getInt("end");
		AstNode matchedNode = info.ast;

//		if (rootNodeType.equals("<ROOT>") || rootNodeType.equals(info.ast.underlyingAstNode.getClass().getSimpleName())) {
//			// '<ROOT>' is a magic node type that always matches the root node.
//			// For ExtendJ the root type is missing line/col info, which breaks some queries
//			// "Fix" by always matching root 'for free', no matter rootStart/rootEnd
//			matchedNode = info.ast;
//		}
//		if (matchedNode == null) {
//		// Gradually increase search span a few columns in either direction
//		for (int offset : Arrays.asList(0, 1, 3, 5, 10)) {
//			matchedNode = bestMatchingNode(info.ast,
//					info.loadAstClass.apply(
//							info.ast.underlyingAstNode.getClass().getPackage().getName() + "." + rootNodeType),
//					rootStart - offset, rootEnd + offset, info.recoveryStrategy, false);
//			if (matchedNode != null) {
//				break;
//			}
//		}
//		}

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
						argsValues[j] = param.getUnpackedIfNode();
						argsTypes[j] = param.paramType;
					}
					Object match = Reflect.invokeN(matchedNode.underlyingAstNode, ntaName, argsTypes, argsValues);
					matchedNode = match == null ? null : new AstNode(match);
					break;
				}
				case "tal": {
					final JSONObject tal = step.getJSONObject("value");
					matchedNode = bestMatchingNode(info, matchedNode,
							info.loadAstClass
									.apply(info.basAstClazz.getPackage().getName() + "." + tal.getString("type")),
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
		Span matchPos = null;
		if (matchedNode != null) {
			try {
				matchPos = Span.extractPosition(info, matchedNode);
			} catch (InvokeProblem e1) {
				System.out.println("Error while extracting position of matched node");
				e1.printStackTrace();
			}
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
