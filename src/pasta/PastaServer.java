package pasta;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pasta.util.ASTProvider;

public class PastaServer {

//	/Users/anton/repo/extendj/java8/extendj.jar
//	/Users/anton/repo/lth/edan65/A6-SimpliC/compiler.jar

	private static void printUsage() {
		System.out.println(
				"Usage: java -jar Pasta.jar path/to/your/compiler.jar [args-to-forward-to-compiler-on-each-request]");
	}

	private static void getContainingSpansAndNodeTypes(List<JSONObject> out, Object astNode, int pos,
			PositionRecoveryStrategy recoveryStrategy) {
		final Position nodePos;
		try {
			nodePos = PositionRecovery.extractPosition(astNode, recoveryStrategy);
		} catch (NoSuchMethodException | InvocationTargetException e1) {
			e1.printStackTrace();
			return;
		}
//		(nodePos.start == 0 && nodePos.end == 0) || 
		if ((nodePos.start <= pos && nodePos.end >= pos)) {
			boolean show = true;
			switch (astNode.getClass().getSimpleName()) {
			case "List":
			case "Opt": {
				show = false; // Default false for these two, they are very rarely useful
			}
			}

			// TODO default for List/Opt should be false
			try {
				show = (Boolean) Reflect.throwingInvoke0(astNode, "pastaVisible");
			} catch (NoSuchMethodException | InvocationTargetException e) {
				// Ignore
			}
			if (show) {
				JSONObject obj = new JSONObject();
				obj.put("start", nodePos.start);
				obj.put("end", nodePos.end);
				obj.put("type", astNode.getClass().getSimpleName());
//				obj.put("pretty", Reflect.invoke0(astNode, "prettyPrint"));
				out.add(obj);
			}
		}
		for (Object child : (Iterable<?>) Reflect.invoke0(astNode, "astChildren")) {
			getContainingSpansAndNodeTypes(out, child, pos, recoveryStrategy);
		}
	}

	private static JSONObject buildLocator(Object astNode, PositionRecoveryStrategy recoveryStrategy)
			throws NoSuchMethodException, InvocationTargetException {
		// TODO add position recovery things here(?)
		final RobustNodeId id;

		try {
			id = RobustNodeId.extract(astNode, recoveryStrategy);
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

	private static void encodeValue(JSONArray out, Object value, Class<?> baseAstType, PositionRecoveryStrategy recoveryStrategy,
			HashSet<Object> alreadyVisitedNodes) {
		if (value == null) {
			out.put("null");
//			new IdentityHashMap<>()
			return;
		}
		// Clone to avoid showing 'already visited' when this encoding 'branch' hasn't
		// visited at all
		alreadyVisitedNodes = new HashSet<Object>(alreadyVisitedNodes);

//		if (value instanceof Collection<?>) {
//			Collection<?> coll = (Collection<?>) value;
////			out.append("[\n");
//			for (Object o : coll) {
//				encodeValue(out, o, ast, recoveryStrategy);
////				out.append(" " + o + "\n");
//			}
//			return;
////			out.append("]\n");
//		}
		if (value != null && baseAstType.isInstance(value)) {
			if (alreadyVisitedNodes.contains(value)) {
				out.put("<< reference loop to already visited value " + value + " >>");
				return;
			}
			try {
				Object preferredView = Reflect.throwingInvoke0(value, "pastaView");
				alreadyVisitedNodes.add(value);
				encodeValue(out, preferredView, baseAstType, recoveryStrategy, alreadyVisitedNodes);
				return;
			} catch (NoSuchMethodException | InvocationTargetException e) {
				// Fall down to default view
			}

			try {
				final JSONObject locator = buildLocator(value, recoveryStrategy);
				if (locator != null) {
					JSONObject wrapper = new JSONObject();
					wrapper.put("type", "node");
					wrapper.put("value", locator);
					out.put(wrapper);

					out.put("\n");
					if (value.getClass().getSimpleName().equals("List")) {
						final int numEntries = (Integer) Reflect.invoke0(value, "getNumChild");
						out.put("");
						if (numEntries == 0) {
							out.put("<empty list>");
						} else {
							out.put("List contents [" + numEntries + "]:");
							for (Object child : (Iterable<?>) Reflect.invoke0(value, "astChildren")) {
								alreadyVisitedNodes.add(value);
								encodeValue(out, child, baseAstType, recoveryStrategy, alreadyVisitedNodes);
							}
						}
						return;
					}

//				out.put(robust.toString(2));
					return;
				}
			} catch (NoSuchMethodException | InvocationTargetException e) {
				// No getStart - this isn't an AST Node!
				// It is just some class that happens to reside in the same package
				// Fall down to default toString encoding below
			}
		}

		if (value instanceof Iterable<?>) {
			if (alreadyVisitedNodes.contains(value)) {
				out.put("<< reference loop to already visited value " + value + " >>");
				return;
			}
			alreadyVisitedNodes.add(value);
			out.put("Iterable [");
			Iterable<?> iter = (Iterable<?>) value;
			for (Object o : iter) {
				encodeValue(out, o, baseAstType, recoveryStrategy, alreadyVisitedNodes);
			}
			out.put("]");
			return;
		}
		if (value instanceof Iterator<?>) {
			if (alreadyVisitedNodes.contains(value)) {
				out.put("<< reference loop to already visited value " + value + " >>");
				return;
			}
			alreadyVisitedNodes.add(value);
			out.put("Iterator [");
			Iterator<?> iter = (Iterator<?>) value;
			while (iter.hasNext()) {
				encodeValue(out, iter.next(), baseAstType, recoveryStrategy, alreadyVisitedNodes);
			}
			out.put("]");
			return;
		}
		try {
			if (value.getClass().getMethod("toString").getDeclaringClass() == Object.class) {
//				if (value.getClass().isEnum()) {
//					out.put(value.toString());
//				}
				out.put("No toString or pastaView() implementation in " + value.getClass().getName());
			}
		} catch (NoSuchMethodException e) {
			System.err.println("No toString implementation for " + value.getClass());
			e.printStackTrace();
		}
		for (String line : (value + "").split("\n")) {
			out.put(line);
		}
	}

	static Object bestMatchingNode(Object astNode, Class<?> nodeType, int startPos, int endPos,
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

		// TODO this if statements makes ExtendJ not work
		// However, without it you can select nodes in completely incorrect locations
		// Not sure what to do here. Special case for start==end==0?
		if (start != 0 && end != 0 && (start > endPos || end < startPos)) {
//			System.out.println("bmnode " + nodeType + " " + start + ", " + end + " vs " + startPos + ", " + endPos
//					+ " : " + astNode.getClass().getName() + ", exiting");
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

	private static boolean onlySimpleParameters(Parameter[] params) {
		final Type[] primitives = new Type[] { Boolean.TYPE, Integer.TYPE, };
		findBadParameter: for (Parameter param : params) {
			final Class<?> type = param.getType();
			for (Type prim : primitives) {
				if (type == prim.getClass()) {
					continue findBadParameter;
				}
			}
			if (type == String.class) {
				continue;
			}
			// Else, possibly AST Node but we don't support that yet
			return false;
		}
		return true;
	}

	public static void main(String[] mainArgs) {
		System.out.println("Starting debug build..");
		if (mainArgs.length == 0) {
			printUsage();
			System.exit(1);
		}
		final String jarPath = mainArgs[0];
		final String[] forwardArgs = Arrays.copyOfRange(mainArgs, 1, mainArgs.length);

		final Function<JSONObject, String> handleQuery = queryObj -> {
			System.out.println("Incoming query: " + queryObj.toString(2));
			final PositionRecoveryStrategy recoveryStrategy = PositionRecoveryStrategy
					.fromRpcParameter(queryObj.getString("posRecovery"));
			final JSONObject queryBody = queryObj.getJSONObject("query");
			final JSONObject queryAttr = queryBody.getJSONObject("attr");
//			if (queryTextParts.length <= 1) {
//				throw new Error("Invalid query text, expected 'Type.attr', got " + queryObj.getString("queryText"));
//			}
//			final int startPos = locator.getJSONObject("root").getInt("start");
//			final int endPos = locator.getJSONObject("root").getInt("end");
//			System.out.println("start, end: " + startPos + "/" + endPos);

//			final int startPos = (queryObj.getInt("lineStart") << 12) | queryObj.getInt("colStart");
//			final int endPos = (queryObj.getInt("lineEnd") << 12) | queryObj.getInt("colEnd");

//			StringBuilder retBuilder = new StringBuilder();
			JSONObject retBuilder = new JSONObject();
			JSONArray bodyBuilder = new JSONArray();
			JSONArray errors = new JSONArray();

			final File tmp;
			try {
				tmp = File.createTempFile("pasta-server", ".java");
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			try {
				Files.write(tmp.toPath(), queryObj.getString("text").getBytes(StandardCharsets.UTF_8),
						StandardOpenOption.CREATE);
				final String[] withFile = new String[1 + forwardArgs.length];
				withFile[0] = tmp.getAbsolutePath();
				System.arraycopy(forwardArgs, 0, withFile, 1, forwardArgs.length);
				System.out.println("fwd args: " + Arrays.toString(withFile));

				StdIoInterceptor rootInterceptor = new StdIoInterceptor() {

					@Override
					public void onLine(boolean stdout, String line) {
						final Matcher matcher = Pattern.compile("ERR@(\\d+);(\\d+);(.*)").matcher(line);
						if (matcher.matches()) {
							final int start = Integer.parseInt(matcher.group(1));
							final int end = Integer.parseInt(matcher.group(2));
							final String msg = matcher.group(3);
							final JSONObject obj = new JSONObject();
							obj.put("start", start);
							obj.put("end", end);
							obj.put("msg", msg);
							errors.put(obj);
						}
					}
				};
				rootInterceptor.install();
				try {

					final boolean parsed = ASTProvider.parseAst(jarPath, withFile, (ast, loadCls) -> {
						if (ast == null) {
							bodyBuilder.put("Failed to parse, but got 'onParse' callback?!");
							bodyBuilder.put("This feels like a bug, tell whoever is developing the pasta server.");
							return;
						}
						System.out.println("Parsed, got: " + ast);
//						System.out.println("ast pkg: " + ast.getClass().getPackage());
						Object matchedNode = null;

						final JSONObject locator = queryBody.getJSONObject("locator");
						final String rootNodeType = locator.getJSONObject("root").getString("type");
						final int rootStart = locator.getJSONObject("root").getInt("start");
						final int rootEnd = locator.getJSONObject("root").getInt("end");
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
										loadCls.apply(ast.getClass().getPackage().getName() + "." + rootNodeType),
										rootStart - offset, rootEnd + offset, recoveryStrategy);
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
								case "znta": {
									matchedNode = Reflect.invoke0(matchedNode, step.getString("value"));
									break;
								}
								case "pnta": {
									final JSONObject mth = step.getJSONObject("value");
									final JSONArray args = mth.getJSONArray("args");
									final Object[] argsValues = new Object[args.length()];
									final Class<?>[] argsTypes = new Class<?>[args.length()];
									for (int j = 0; j < args.length(); ++j) {
										final String value = args.getString(j);
										argsValues[j] = value;
										argsTypes[j] = String.class; // Only support string for now
									}
									matchedNode = Reflect.invokeN(matchedNode, mth.getString("name"), argsTypes,
											argsValues);
									break;
								}
								case "tloc": {
									final JSONObject tloc = step.getJSONObject("value");
									matchedNode = bestMatchingNode(matchedNode,
											loadCls.apply(ast.getClass().getPackage().getName() + "."
													+ tloc.getString("type")),
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

						System.out.println("MatchedNode: " + matchedNode);
						if (matchedNode == null || matchPos == null) {
							bodyBuilder.put(
									"No matching node found\n\nTry remaking the probe\nat a different line/column");
							return;
						}
//						
//						final int matchStart = matchPos.start;
//						final int matchEnd = matchPos.end;
//						retBuilder.put("matchStart", matchStart);
//						retBuilder.put("matchEnd", matchEnd);
						try {
							final JSONObject matchedNodeLocator = buildLocator(matchedNode, recoveryStrategy);
							if (matchedNodeLocator != null) {
								retBuilder.put("locator", matchedNodeLocator);
							}
						} catch (JSONException | NoSuchMethodException | InvocationTargetException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}

//						String queryTail = queryTextParts[1];
//						for (int i = 2; i < queryTextParts.length; i++) {
//							queryTail += "." + queryTextParts[i];
//						}

						final String queryAttrName = queryAttr.getString("name");
						switch (queryAttrName) {
						// TODO rename magic attribtues to something that is less likely to collide with
						// normal AST attrs
						case "pasta_containingSpansAndNodeTypes": {
//							JSONArray 
							final List<JSONObject> arr = new ArrayList<>();
							getContainingSpansAndNodeTypes(arr, matchedNode, rootStart + (rootEnd - rootStart) / 2,
									recoveryStrategy);
							JSONArray jsonArr = new JSONArray();
							for (int i = arr.size() - 1; i >= 0; --i) {
								jsonArr.put(arr.get(i));
							}
							retBuilder.put("spansAndNodeTypes", jsonArr);
							break;
						}
						case "pastaAttrs": {
							List<String> filter = null;
							try {
								final Object override = Reflect.throwingInvoke0(matchedNode, "pastaAttrs");
								if (override instanceof Collection<?>) {
									filter = new ArrayList<String>((Collection<String>) override);
//									retBuilder.put("pastaAttrs", new JSONArray((Collection<?>) override));
//									break;
								}
							} catch (NoSuchMethodException | InvocationTargetException e) {
							}

//								e.printStackTrace();
							// No attr filter, pick all attrs instead.
							List<JSONObject> attrs = new ArrayList<>();
//								attrs.add("[");
							for (Method m : matchedNode.getClass().getMethods()) {
								if (filter != null && !filter.contains(m.getName())) {
									continue;
								}
								if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
									continue;
								}
								final Parameter[] parameters = m.getParameters();
								if (!onlySimpleParameters(parameters)) {
									continue;
								}
								if (Pattern.compile(".*(\\$).*").matcher(m.getName()).matches()) {
									continue;
								}
								if (parameters.length > 0) {
									System.out.println("m w/ ars: " + m.toString());
								}
								JSONObject attr = new JSONObject();
								attr.put("name", m.getName());

								JSONArray args = new JSONArray();
								for (Parameter p : parameters) {
									JSONObject arg = new JSONObject();
									arg.put("name", p.getName());
									arg.put("type", p.getType().getName());
									args.put(arg);
//									args.put(p.getName())
								}
								attr.put("args", args);

								attrs.add(attr);
//										boolean isAttribute = false;
//										for (Annotation a : m.getAnnotations()) {
//											if (a.annotationType().getSimpleName().equals("Attribute")) {
//												isAttribute = true;
//												break;
//											}
//										}
//										if (isAttribute) {
//										}
							}
							attrs.sort((a, b) -> a.getString("name").compareTo(b.getString("name")));
//								attrs.add("]");
//								encodeValue(retBuilder, attrs, ast);
							JSONArray json = new JSONArray();
							for (JSONObject o : attrs) {
								json.put(o);
							}
							retBuilder.put("pastaAttrs", json);
							break;
//							break;
						}
						default: {
							StdIoInterceptor probeInterceptor = null;
							final boolean captureStdio = queryObj.optBoolean("stdout", false);
							if (captureStdio) {
								probeInterceptor = new StdIoInterceptor() {

									@Override
									public void onLine(boolean stdout, String line) {
										JSONObject fmt = new JSONObject();
										fmt.put("type", stdout ? "stdout" : "stderr");
										fmt.put("value", line);
										bodyBuilder.put(fmt);
									}
								};
								probeInterceptor.install();
							}
							try {
								final JSONArray args = queryAttr.optJSONArray("args");
								final Object value;
								if (args == null) {
									value = Reflect.throwingInvoke0(matchedNode, queryAttrName);
								} else {
									final int numArgs = args.length();
									final Class<?>[] argTypes = new Class<?>[numArgs];
									final Object[] argValues = new Object[numArgs];
									for (int i = 0; i < numArgs; ++i) {
										final JSONObject arg = args.getJSONObject(i);
										switch (arg.getString("type")) {
										case "java.lang.String": {
											argTypes[i] = String.class;
											argValues[i] = arg.optString("value"); // opt to allow nullable values
											break;
										}
										default: {
											bodyBuilder
													.put("Cannot use attribute type '" + arg.getString("type") + "'");
											break;
										}
										}
									}
									value = Reflect.invokeN(matchedNode, queryAttrName, argTypes, argValues);
								}
								Class<?> baseAstType = ast.getClass();
								while (true) {
									Class<?> parentType = baseAstType.getSuperclass();
									if (parentType == null || !parentType.getPackage().getName().equals(baseAstType.getPackage().getName())) {
										break;
									}
									baseAstType = parentType;
								}
								encodeValue(bodyBuilder, value, baseAstType, recoveryStrategy, new HashSet<>());
							} catch (InvocationTargetException e) {
								if (e.getCause() != null) {
									e.getCause().printStackTrace();
								} else {
									e.printStackTrace();
								}
								bodyBuilder.put("Exception thrown while evaluating attribute.");
								if (!captureStdio) {
									bodyBuilder.put("Click 'Capture stdio' to see full error.");
								}
							} catch (NoSuchMethodException e) {
								bodyBuilder.put("No such attribute '" + queryAttrName + "' on "
										+ matchedNode.getClass().getSimpleName());
							} finally {
								if (probeInterceptor != null) {
									probeInterceptor.flush();
									probeInterceptor.restore();
								}
							}
						}
						}
					});
					if (!parsed) {
						System.out.println("Parsing failed..");
					}
					if (!parsed && retBuilder.length() > 0) {
						bodyBuilder.put("🍝 Probe error");
					}
				} finally {
					rootInterceptor.flush();
					rootInterceptor.restore();
				}
			} catch (JSONException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				tmp.delete();
			}

			// Somehow extract syntax errors from stdout?
			retBuilder.put("body", bodyBuilder);
			retBuilder.put("errors", errors);
			if (retBuilder.length() == 0) {
				return "{\"type\":\"rpc\",\"id\":" + queryObj.getLong("id")
						+ ",\"result\":\"--------\\n💥 Evaluation  failed, look at Pasta server log\"}";
			}
			JSONObject resp = new JSONObject();
			resp.put("type", "rpc");
			resp.put("id", queryObj.getLong("id"));
			resp.put("result", retBuilder.toString());
			return resp.toString();
//			return ;
		};

		new Thread(WebServer::start).start();

		final List<Runnable> onJarChangeListeners = Collections.synchronizedList(new ArrayList<>());
		new Thread(() -> WebSocketServer.start(onJarChangeListeners, handleQuery)).start();

		System.out.println("Starting file monitor...");
		new FileMonitor(new File(jarPath)) {
			public void onChange() {
				System.out.println("Jar changed!!!");
				synchronized (onJarChangeListeners) {
					onJarChangeListeners.forEach(Runnable::run);
				}
			};
		}.start();
	}

}
