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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pasta.ResolveNodeLocator.ResolvedNode;
import pasta.protocol.SerializableParameter;
import pasta.protocol.SerializableParameterType;
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

	private static void encodeValue(JSONArray out, Object value, Class<?> baseAstType,
			PositionRecoveryStrategy recoveryStrategy, HashSet<Object> alreadyVisitedNodes) {
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
				final JSONObject locator = ResolveNodeLocator.buildLocator(value, recoveryStrategy, baseAstType);
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

			JSONArray indent = new JSONArray();
			Iterable<?> iter = (Iterable<?>) value;
			for (Object o : iter) {
				encodeValue(indent, o, baseAstType, recoveryStrategy, alreadyVisitedNodes);
			}
			out.put(indent);
			return;
		}
		if (value instanceof Iterator<?>) {
			if (alreadyVisitedNodes.contains(value)) {
				out.put("<< reference loop to already visited value " + value + " >>");
				return;
			}
			alreadyVisitedNodes.add(value);
			JSONArray indent = new JSONArray();
			Iterator<?> iter = (Iterator<?>) value;
			while (iter.hasNext()) {
				encodeValue(indent, iter.next(), baseAstType, recoveryStrategy, alreadyVisitedNodes);
			}
			out.put(indent);
			return;
		}
//		if (value instanceof Object[]) {
//			if (alreadyVisitedNodes.contains(value)) {
//				out.put("<< reference loop to already visited value " + value + " >>");
//				return;
//			}
//			alreadyVisitedNodes.add(value);
//			
//			final JSONArray indent = new JSONArray();
//			for (Object child : (Object[])value) {
//				encodeValue(indent, child, baseAstType, recoveryStrategy, alreadyVisitedNodes, false);
//			}
//			final JSONObject indentObj = new JSONObject();
//			indentObj.put("type", "indent");
//			indentObj.put("value", indent);
//			out.put(indentObj);
//			return;
//		}
		try {
			if (value.getClass().getMethod("toString").getDeclaringClass() == Object.class) {
//				if (value.getClass().isEnum()) {
//					out.put(value.toString());
//				}
				out.put("No toString() or pastaView() implementation in " + value.getClass().getName());
			}
		} catch (NoSuchMethodException e) {
			System.err.println("No toString implementation for " + value.getClass());
			e.printStackTrace();
		}
		for (String line : (value + "").split("\n")) {
			out.put(line);
		}
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
						final Matcher squiggly = Pattern
								.compile("(ERR|WARN|INFO|LINE-PP|LINE-AA|LINE-AP|LINE-PA)@(\\d+);(\\d+);(.*)")
								.matcher(line);
						if (squiggly.matches()) {
							final int start = Integer.parseInt(squiggly.group(2));
							final int end = Integer.parseInt(squiggly.group(3));
							final String msg = squiggly.group(4);
							final JSONObject obj = new JSONObject();
							switch (squiggly.group(1)) {
							case "ERR":
								obj.put("severity", "error");
								break;
							case "WARN":
								obj.put("severity", "warning");
								break;
							default:
								obj.put("severity", squiggly.group(1).toLowerCase(Locale.ENGLISH));
								break;
							}
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
							bodyBuilder.put("Compiler exited normally, but no 'DrAST_root_node' found");
							bodyBuilder.put(
									"If you call System.exit(<not zero>) when parsing fails then this message will disappear.");
							return;
						}
						System.out.println("Parsed, got: " + ast);

						Class<?> baseAstType = ast.getClass();
						while (true) {
							Class<?> parentType = baseAstType.getSuperclass();
							if (parentType == null
									|| !parentType.getPackage().getName().equals(baseAstType.getPackage().getName())) {
								break;
							}
							baseAstType = parentType;
						}
//						System.out.println("ast pkg: " + ast.getClass().getPackage());

						final JSONObject locator = queryBody.getJSONObject("locator");
						ResolvedNode match = ResolveNodeLocator.resolve(ast, recoveryStrategy, loadCls, locator,
								baseAstType);

						System.out.println("MatchedNode: " + match);
						if (match == null) {
							bodyBuilder.put(
									"No matching node found\n\nTry remaking the probe\nat a different line/column");
							return;
						}

						final String queryAttrName = queryAttr.getString("name");
						switch (queryAttrName) {
						case "pasta_containingSpansAndNodeTypes": {
//							JSONArray 
							final List<JSONObject> arr = new ArrayList<>();
							final int rootStart = locator.getJSONObject("root").getInt("start");
							final int rootEnd = locator.getJSONObject("root").getInt("end");
							getContainingSpansAndNodeTypes(arr, match.node, rootStart + (rootEnd - rootStart) / 2,
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
								final Object override = Reflect.throwingInvoke0(match.node, "pastaAttrs");
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
							for (Method m : match.node.getClass().getMethods()) {
								if (filter != null && !filter.contains(m.getName())) {
									continue;
								}
								if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
									continue;
								}
								if (Pattern.compile(".*(\\$|_).*").matcher(m.getName()).matches()) {
									continue;
								}
								final Parameter[] parameters = m.getParameters();
								final SerializableParameterType[] serializableParamTypes = SerializableParameterType
										.decodeParameters(parameters, baseAstType);
								if (serializableParamTypes == null) {
									System.out.println("Skipping " + m + ", unknown param types");
									continue;
								}
								if (parameters.length > 0) {
									System.out.println("m w/ args: " + m.toString());
								}
								JSONObject attr = new JSONObject();
								attr.put("name", m.getName());

								JSONArray args = new JSONArray();

								for (int i = 0; i < parameters.length; ++i) {
									JSONObject arg = new JSONObject();
									arg.put("name", parameters[i].getName());
									serializableParamTypes[i].serializeTo(arg);
									args.put(arg);
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
									value = Reflect.throwingInvoke0(match.node, queryAttrName);
								} else {
									final int numArgs = args.length();
									final Class<?>[] argTypes = new Class<?>[numArgs];
									final Object[] argValues = new Object[numArgs];
									for (int i = 0; i < numArgs; ++i) {
										final SerializableParameter param = SerializableParameter.decode(
												args.getJSONObject(i), ast, recoveryStrategy, loadCls, baseAstType);
										if (param == null) {
											bodyBuilder.put("Failed decoding parameter " + i);
											if (!captureStdio) {
												bodyBuilder.put("Click 'Capture stdio' to see more information.");
											}
											return;
										}
										argTypes[i] = param.paramType;
										argValues[i] = param.value;
									}
									value = Reflect.invokeN(match.node, queryAttrName, argTypes, argValues);
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
										+ match.node.getClass().getSimpleName());
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
