package pasta;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pasta.protocol.SerializableParameter;

public class ResolveNodeLocator {

	public static class ResolvedNode {
		public final Object node;
		public final Position pos;
		public final JSONObject nodeLocator;

		public ResolvedNode(Object node, Position pos, JSONObject nodeLocator) {
			this.node = node;
			this.pos = pos;
			this.nodeLocator = nodeLocator;
		}
	}

	public static JSONObject buildLocator(Object astNode, PositionRecoveryStrategy recoveryStrategy, Class<?> baseAstClazz)
			throws NoSuchMethodException, InvocationTargetException {
		// TODO add position recovery things here(?)
		final RobustNodeId id;

		try {
			id = RobustNodeId.extract(astNode, recoveryStrategy, baseAstClazz);
		} catch (RuntimeException e) {
			System.out.println("Failed to extract locator for " + astNode);
			e.printStackTrace();
			return null;
		}

		final JSONObject robustRoot = new JSONObject();
		robustRoot.put("start", id.root.position.start);
		robustRoot.put("end", id.root.position.end);
		robustRoot.put("type", id.root.type);

		final JSONObject robustResult = new JSONObject();
		final Position astPos = PositionRecovery.extractPosition(astNode, recoveryStrategy);
		robustResult.put("start", astPos.start);
		robustResult.put("end", astPos.end);
		robustResult.put("type", astNode.getClass().getSimpleName());

		final JSONArray steps = new JSONArray();
		for (Edge step : id.steps) {
			step.encode(steps);
		}

		final JSONObject robust = new JSONObject();
		robust.put("root", robustRoot);
		robust.put("result", robustResult);
		robust.put("steps", steps);
//		System.out.println("Locator: " + robust.toString(2));
		return robust;
	}

	private static Object bestMatchingNode(Object astNode, Class<?> nodeType, int startPos, int endPos,
			PositionRecoveryStrategy recoveryStrategy) {
		final Position nodePos;
		try {
			nodePos = PositionRecovery.extractPosition(astNode, recoveryStrategy);
		} catch (NoSuchMethodException | InvocationTargetException e) {
			System.out.println("Error while extracting node position");
			e.printStackTrace();
			return null;
		}
		final int start = nodePos.start;
		final int end = nodePos.end;

		if (start != 0 && end != 0 && (start > endPos || end < startPos)) {
//			// Assume that a parent only contains children within its own bounds
			return null;
		}

		Object bestNode = nodeType.isInstance(astNode) ? astNode : null;
		int bestError = bestNode != null ? (Math.abs(start - startPos) + Math.abs(end - endPos)) : Integer.MAX_VALUE;
		for (Object child : (Iterable<?>) Reflect.invoke0(astNode, "astChildren")) {
			Object recurse = bestMatchingNode(child, nodeType, startPos, endPos, recoveryStrategy);
			if (recurse != null) {
				final Position recursePos;
				try {
					recursePos = PositionRecovery.extractPosition(recurse, recoveryStrategy);
					final int recurseError = Math.abs(recursePos.start - startPos) + Math.abs(recursePos.end - endPos);
					if (recurseError < bestError) {
						bestNode = recurse;
						bestError = recurseError;
//					} else if (recurseError == bestError) {
//						// Two seemingly identical paths forward
//						// Heuristic/guess: the one with natural position information is better
//						final Position nonRecoveredBestPos = Position.from(bestNode);
//						if (!nonRecoveredBestPos.isMeaningful()) {
//							final Position nonRecoveredRecursePos = Position.from(recurse);
//							if (nonRecoveredRecursePos.isMeaningful()) {
//								bestNode = recurse;
//								bestError = recurseError;
//							}
//						}
					}
				} catch (NoSuchMethodException | InvocationTargetException e) {
					System.out.println("Error while extracting child node position");
					e.printStackTrace();
				}
			}
		}
		return bestNode;
	}

	public static ResolvedNode resolve(Object ast, PositionRecoveryStrategy recoveryStrategy,
			Function<String, Class<?>> loadCls, JSONObject locator, Class<?> baseAstClazz) {

		final String rootNodeType = locator.getJSONObject("root").getString("type");
		final int rootStart = locator.getJSONObject("root").getInt("start");
		final int rootEnd = locator.getJSONObject("root").getInt("end");
		Object matchedNode = null;

		// Gradually increase search span a few columns in either direction
		if (rootNodeType.equals(ast.getClass().getSimpleName())) {
			// Assume that an AST has a unique root type
			// For ExtendJ the root type is missing line/col info, which breaks some queries
			// "Fix" by always matching root 'for free', no matter rootStart/rootEnd
			matchedNode = ast;
		}
		if (matchedNode == null) {
			for (int offset : Arrays.asList(0, 1, 3, 5, 10)) {
				matchedNode = bestMatchingNode(ast,
						loadCls.apply(ast.getClass().getPackage().getName() + "." + rootNodeType), rootStart - offset,
						rootEnd + offset, recoveryStrategy);
				if (matchedNode != null) {
					break;
				}
			}
		}

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
					System.out.println("decoding args for nta, argsArr: " + args);
					for (int j = 0; j < args.length(); ++j) {
						final SerializableParameter param = SerializableParameter.decode(
								args.getJSONObject(j), ast, recoveryStrategy, loadCls, baseAstClazz);
						if (param == null) {
							System.out.println("Failed decoding parameter " + i +" for NTA '" + ntaName +"'");
							return null;
						}
						argsValues[j] = param.value;
						argsTypes[j] = param.paramType;
					}
					matchedNode = Reflect.invokeN(matchedNode, ntaName, argsTypes, argsValues);
					break;
				}
				case "tloc": {
					final JSONObject tloc = step.getJSONObject("value");
					matchedNode = bestMatchingNode(matchedNode,
							loadCls.apply(ast.getClass().getPackage().getName() + "." + tloc.getString("type")),
							tloc.getInt("start"), tloc.getInt("end"), recoveryStrategy);
					break;
				}
				case "child": {
					final int childIndex = step.getInt("value");
					matchedNode = Reflect.getNthChild(matchedNode, childIndex);
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
		Position matchPos = null;
		if (matchedNode != null) {
			try {
				matchPos = PositionRecovery.extractPosition(matchedNode, recoveryStrategy);
			} catch (NoSuchMethodException | InvocationTargetException e1) {
				System.out.println("Error while extracting position of matched node");
				e1.printStackTrace();
			}
		}
		JSONObject matchedNodeLocator = null;
		if (matchedNode != null && matchPos != null) {
			try {
				matchedNodeLocator = buildLocator(matchedNode, recoveryStrategy, baseAstClazz);
//			if (matchedNodeLocator != null) {
//				retBuilder.put("locator", matchedNodeLocator);
//			}
			} catch (JSONException | NoSuchMethodException | InvocationTargetException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		if (matchedNode == null || matchPos == null || matchedNodeLocator == null) {
			return null;
		}
		return new ResolvedNode(matchedNode, matchPos, matchedNodeLocator);
	}
}
