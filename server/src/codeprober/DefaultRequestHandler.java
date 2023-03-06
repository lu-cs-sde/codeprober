package codeprober;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.ast.AstNode;
import codeprober.locator.ApplyLocator;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.locator.CreateLocator.LocatorMergeMethod;
import codeprober.locator.AttrsInNode;
import codeprober.locator.CreateLocator;
import codeprober.locator.ListTree;
import codeprober.locator.NodesAtPosition;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.AstNodeApiStyle;
import codeprober.metaprogramming.Reflect;
import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.metaprogramming.StreamInterceptor;
import codeprober.metaprogramming.TypeIdentificationStyle;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.ParameterValue;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.create.EncodeResponseValue;
import codeprober.protocol.decode.DecodeValue;
import codeprober.rpc.JsonRequestHandler;
import codeprober.util.ASTProvider;
import codeprober.util.BenchmarkTimer;
import codeprober.util.MagicStdoutMessageParser;

public class DefaultRequestHandler implements JsonRequestHandler {

	private final String underlyingCompilerJar;
	private final String[] defaultForwardArgs;

	private AstInfo lastInfo = null;
	private String lastParsedInput = null;
	private String[] lastForwardArgs;

	public DefaultRequestHandler(String underlyingJarFile, String[] forwardArgs) {
		this.underlyingCompilerJar = underlyingJarFile;
		this.defaultForwardArgs = forwardArgs;
		this.lastForwardArgs = this.defaultForwardArgs;
	}

	void handleParsedAst(Object ast, Function<String, Class<?>> loadAstClass, JSONObject queryObj,
			JSONObject retBuilder, JSONArray bodyBuilder) {
		if (ast == null) {
			lastInfo = null;
			bodyBuilder.put("Compiler exited, but no 'DrAST_root_node' found.");
			bodyBuilder
					.put("If parsing failed, you can draw 'red squigglies' in the code to indicate where it failed.");
			bodyBuilder.put("See overflow menu (⠇) -> \"Magic output messages help\".");
			bodyBuilder.put(
					"If parsing succeeded, make sure you declare and assign the following field in your main class:");
			bodyBuilder.put("'public static Object DrAST_root_node'");
//			bodyBuilder
//			bodyBuilder.put("If you call System.exit(<not zero>) when parsing fails then this message will disappear.");
			return;
		}
		System.out.println("Parsed, got: " + ast);
		AstNode astNode = new AstNode(ast);

		AstNodeApiStyle positionRepresentation = null;
		try {
			if (Reflect.invoke0(ast, "getStartLine") instanceof Integer) {
				positionRepresentation = AstNodeApiStyle.JASTADD_SEPARATE_LINE_COLUMN;
			}
		} catch (RuntimeException e) {
			// Not getStartLine..
		}
		if (positionRepresentation == null) {
			try {
				if (Reflect.invoke0(ast, "getBeginLine") instanceof Integer) {
					positionRepresentation = AstNodeApiStyle.PMD_SEPARATE_LINE_COLUMN;
				}
			} catch (RuntimeException e) {
				// Not getBeginLine..
			}
		}
		if (positionRepresentation == null) {
			if (Reflect.invoke0(ast, "getStart") instanceof Integer) {
				positionRepresentation = AstNodeApiStyle.BEAVER_PACKED_BITS;
			}
		}
		if (positionRepresentation == null) {
			System.out.println("Unable to determine how position is stored in the AST, exiting. Expected one of:");
			System.out.println(
					"1) [getStart, getEnd], should return a packed line/column integer, 20 bits line and 12 bits column, 0xLLLLLCCC");
			System.out.println(
					"2) [getStartLine, getEndLine, getStartColumn, getEndColumn] should return line / column respectively.");
			throw new RuntimeException("Exiting due to unknown position representation");
		}

		final AstInfo info = new AstInfo(astNode,
				PositionRecoveryStrategy.fallbackParse(queryObj.getString("posRecovery")), positionRepresentation,
				loadAstClass, TypeIdentificationStyle.parse(System.getProperty("CPR.TYPE_IDENTIFICATION_STYLE")));
		lastInfo = info;

		final JSONObject queryBody = queryObj.getJSONObject("query");
		final JSONObject locator = queryBody.getJSONObject("locator");
		ResolvedNode match = ApplyLocator.toNode(info, locator);

//		System.out.println("MatchedNode: " + match);
		if (match == null) {
			bodyBuilder.put("No matching node found\n\nTry remaking the probe\nat a different line/column");
			return;
		}

		retBuilder.put("locator", match.nodeLocator);

		final JSONObject queryAttr = queryBody.getJSONObject("attr");
		final String queryAttrName = queryAttr.getString("name");
		// First check for 'magic' methods
		switch (queryAttrName) {
		case "meta:listNodes": {
			final int rootStart = locator.getJSONObject("result").getInt("start");
			final int rootEnd = locator.getJSONObject("result").getInt("end");
			BenchmarkTimer.LIST_NODES.enter();
			CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
			try {
				retBuilder.put("nodes", new JSONArray(NodesAtPosition.get( //
						info, match.node, rootStart + (rootEnd - rootStart) / 2 //
				)));
			} finally {
				CreateLocator.setMergeMethod(LocatorMergeMethod.DEFAULT_METHOD);
				BenchmarkTimer.LIST_NODES.exit();
			}
			return;
		}
		case "meta:listTreeDownwards": {
			CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
			try {
				final JSONObject listing = ListTree.listDownwards(info, match.node, 32);
				if (listing == null) {
					bodyBuilder.put("Failed listing tree, see console for more information.");
					return;
				}
				retBuilder.put("nodes", listing);
			} finally {
				CreateLocator.setMergeMethod(LocatorMergeMethod.DEFAULT_METHOD);
			}
			return;
		}
		case "meta:listTreeUpwards": {
			CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
			try {
				final JSONObject listing = ListTree.listUpwards(info, match.node);
				if (listing == null) {
					bodyBuilder.put("Failed listing tree, see console for more information.");
					return;
				}
				retBuilder.put("nodes", listing);
			} finally {
				CreateLocator.setMergeMethod(LocatorMergeMethod.DEFAULT_METHOD);
			}
			return;
		}

		case "meta:listAllProperties": // Fall through
		case "meta:listProperties": {
			BenchmarkTimer.LIST_PROPERTIES.enter();
			try {
				retBuilder.put("properties", AttrsInNode.get(info, match.node,
						AttrsInNode.extractFilter(info, match.node), queryAttrName.equals("meta:listAllProperties")));
			} finally {
				BenchmarkTimer.LIST_PROPERTIES.exit();
			}
			return;
		}
		}

		final boolean captureStdio = queryObj.optBoolean("stdout", false);
		final Runnable evaluateAttr = () -> {

			try {
				final JSONArray args = queryAttr.optJSONArray("args");
				final Object value;

				// Respond with new args, just like we respond with a new locator
				JSONArray updatedArgs = new JSONArray();

				if (queryAttrName.startsWith("l:")) {
					// Special labeled zero-arg attr invocation
					BenchmarkTimer.EVALUATE_ATTR.enter();
					try {
						value = Reflect.invokeN(match.node.underlyingAstNode, "cpr_lInvoke",
								new Class[] { String.class }, new Object[] { queryAttrName.substring(2) });
					} finally {
						BenchmarkTimer.EVALUATE_ATTR.exit();
					}

				} else if (args == null) {
					BenchmarkTimer.EVALUATE_ATTR.enter();
					try {
						value = Reflect.invoke0(match.node.underlyingAstNode, queryAttrName);
					} finally {
						BenchmarkTimer.EVALUATE_ATTR.exit();
					}
				} else {
					final int numArgs = args.length();
					final Class<?>[] argTypes = new Class<?>[numArgs];
					final Object[] argValues = new Object[numArgs];
					for (int i = 0; i < numArgs; ++i) {
						final ParameterValue param = DecodeValue.decode(info, args.getJSONObject(i), bodyBuilder);
						if (param == null) {
							bodyBuilder.put("Failed decoding parameter " + i);
							if (!captureStdio) {
								bodyBuilder.put("Click 'Capture stdout' to see more information.");
							}
							return;
						}
						argTypes[i] = param.paramType;
						argValues[i] = param.getUnpackedValue();
						updatedArgs.put(param.toJson());
					}
					BenchmarkTimer.EVALUATE_ATTR.enter();
					try {
						value = Reflect.invokeN(match.node.underlyingAstNode, queryAttrName, argTypes, argValues);
					} finally {
						BenchmarkTimer.EVALUATE_ATTR.exit();
					}
					for (Object argValue : argValues) {
						// Flush all StreamInterceptor args
						if (argValue instanceof StreamInterceptor) {
							((StreamInterceptor) argValue).consume();
						}
					}
				}

				if (value != Reflect.VOID_RETURN_VALUE) {
					CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
					try {
						EncodeResponseValue.encode(info, bodyBuilder, value, new HashSet<>());
					} finally {
						CreateLocator.setMergeMethod(LocatorMergeMethod.DEFAULT_METHOD);
					}
				}
				retBuilder.put("args", updatedArgs);
			} catch (InvokeProblem e) {
				final Throwable cause = e.getCause();
				if (cause instanceof NoSuchMethodException) {
					bodyBuilder.put("No such attribute '" + queryAttrName + "' on "
							+ match.node.underlyingAstNode.getClass().getName());
				} else {
					if (cause != null && cause.getCause() != null) {
						cause.getCause().printStackTrace();
					} else {
						(cause != null ? cause : e).printStackTrace();
					}
					bodyBuilder.put("Exception thrown while evaluating attribute.");
					if (!captureStdio) {
						bodyBuilder.put("Click 'Capture stdout' to see full error.");
					}
				}
			}
		};

		if (captureStdio) {
			// TODO add messages live instead of after everything finishes
			final BiFunction<Boolean, String, JSONObject> encoder = StdIoInterceptor.createDefaultLineEncoder();
			StdIoInterceptor.performLiveCaptured((stdout, line) -> {
				bodyBuilder.put(encoder.apply(stdout, line));
			}, evaluateAttr);
		} else {
			evaluateAttr.run();
		}
	}

	@Override
	public JSONObject handleRequest(JSONObject queryObj) {
		switch (queryObj.getString("type")) {
		case "query":
			// Break & fall down to implementation below
			break;

		case "fetch":
			// Client needs to bypass cors
			try {
				final URL url = new URL(queryObj.getString("url"));
				final HttpURLConnection con = (HttpURLConnection) url.openConnection();
				con.setRequestMethod("GET");
				con.setConnectTimeout(5000);
				con.setReadTimeout(5000);

				int status = con.getResponseCode();
				if (status != 200) {
					throw new RuntimeException("Unexpected status code " + status);
				}
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				String inputLine;
				final StringBuffer content = new StringBuffer();
				while ((inputLine = in.readLine()) != null) {
					content.append(inputLine + "\n");
				}
				JSONObject res = new JSONObject();
				res.put("result", content.toString());
				con.disconnect();

				return res;
			} catch (IOException e) {
				System.out.println("Error when performing fetch request " + queryObj);
				e.printStackTrace();
				throw new RuntimeException(e);
			}

		default:
			throw new RuntimeException("Invalid request type on " + queryObj);
		}

//		System.out.println("Incoming query: " + queryObj.toString(2));
		final long requestStart = System.nanoTime();

		final AstCacheStrategy cacheStrategy = AstCacheStrategy.fallbackParse(queryObj.getString("cache"));
		if (cacheStrategy == AstCacheStrategy.PURGE) {
			ASTProvider.purgeCache();
			lastInfo = null;
		}

		// Root response object
		final JSONObject retBuilder = new JSONObject();

		// The "body" of the probe, can be seen as 'stdout'. Add information here that
		// the user wants to see.
		// Meta-information goes in the 'root' response object.
		final JSONArray bodyBuilder = new JSONArray();
		BenchmarkTimer.resetAll();
		final JSONArray errors;

		final AtomicReference<File> tmp = new AtomicReference<>(null);
		Function<String, File> createTmpFile = inputText -> {
			final File existing = tmp.get();
			if (existing != null) {
				return existing;
			}
			try {
				final File tmpFile = File.createTempFile("code-prober", queryObj.getString("tmpSuffix"));
				try {
					Files.write(tmpFile.toPath(), inputText.getBytes(StandardCharsets.UTF_8),
							StandardOpenOption.CREATE);
				} catch (IOException e) {
					System.out.println("Failed while copying source text to disk");
					e.printStackTrace();
					throw new RuntimeException(e);
				}
				tmp.set(tmpFile);
				return tmpFile;
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		};
		try {
			errors = StdIoInterceptor.performCaptured(MagicStdoutMessageParser::parse, () -> {
				final String inputText = queryObj.getString("text");

				final String[] fwdArgs;
				final JSONArray argsOverride = queryObj.optJSONArray("mainArgs");
				if (argsOverride != null) {
					fwdArgs = new String[argsOverride.length()];
					for (int i = 0; i < fwdArgs.length; ++i) {
						fwdArgs[i] = argsOverride.getString(i);
					}
				} else {
					fwdArgs = defaultForwardArgs;
				}

				final boolean newFwdArgs = !Arrays.equals(lastForwardArgs, fwdArgs);
				boolean maybeCacheAST = cacheStrategy.canCacheAST() //
						&& lastParsedInput != null //
						&& lastInfo != null //
						&& !newFwdArgs && ASTProvider.hasUnchangedJar(underlyingCompilerJar);

				if (maybeCacheAST && !lastParsedInput.equals(inputText)) {
					System.out.println("Can cache AST, but input is different..");
					maybeCacheAST = false;
					// Something changed, must replace the AST,
					// UNLESS flushTreeCacheAndReplaceLastFile is present.
					Method optimizedFlusher = null;
					try {
						optimizedFlusher = lastInfo.ast.underlyingAstNode.getClass()
								.getMethod("flushTreeCacheAndReplaceLastFile", String.class);
					} catch (NoSuchMethodException | SecurityException e) {
						// OK, it is an optional method after all
						System.out.println("No flusher available");
					}
					if (optimizedFlusher != null) {
						try {
							final File tmpFile = createTmpFile.apply(inputText);
							final long flushStart = System.nanoTime();
							final Boolean replacedOk = (Boolean) optimizedFlusher.invoke(lastInfo.ast.underlyingAstNode,
									tmpFile.getAbsolutePath());
							System.out.println("Tried optimized flush, result: " + replacedOk);
							if (replacedOk) {
								retBuilder.put("parseTime", (System.nanoTime() - flushStart));
								handleParsedAst(lastInfo.ast.underlyingAstNode, lastInfo.loadAstClass, queryObj,
										retBuilder, bodyBuilder);
								lastParsedInput = inputText;
								return;
							}
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
							System.out.println("Error when calling 'flushTreeCacheAndReplaceLastFile'");
							e.printStackTrace();
						}
					}
				}

				if (maybeCacheAST) {
					final long flushStart = System.nanoTime();
					try {
						if (cacheStrategy == AstCacheStrategy.PARTIAL) {
							Reflect.invoke0(lastInfo.ast.underlyingAstNode, "flushTreeCache");
						}

						retBuilder.put("parseTime", (System.nanoTime() - flushStart));
//						final boolean parsed = ASTProvider.parseAst(underlyingCompilerJar, astArgs, (ast, loadCls) -> {
						handleParsedAst(lastInfo.ast.underlyingAstNode, lastInfo.loadAstClass, queryObj, retBuilder,
								bodyBuilder);
						return;
					} catch (InvokeProblem ip) {
						System.out.println("Problem when flushing previous tree");
						ip.printStackTrace();
						lastInfo = null;
					}
				}
				lastParsedInput = inputText;

				final File tmpFile = createTmpFile.apply(inputText);
				final String[] astArgs = new String[1 + fwdArgs.length];
				System.arraycopy(fwdArgs, 0, astArgs, 0, fwdArgs.length);
				astArgs[fwdArgs.length] = tmpFile.getAbsolutePath();
				lastForwardArgs = fwdArgs;

				final long parseStart = System.nanoTime();

				final ASTProvider.ParseResult parsed = ASTProvider.parseAst(underlyingCompilerJar, astArgs,
						(ast, loadCls) -> {
							retBuilder.put("parseTime", (System.nanoTime() - parseStart));
							handleParsedAst(ast, loadCls, queryObj, retBuilder, bodyBuilder);
						});
				if (!parsed.success) {
					if (bodyBuilder.length() == 0) {
						bodyBuilder.put("Parsing failed");
					}
					// Consider this sequence of requests:
					// 1) Successful parse -> lastInfo set
					// 2) Failed parse
					// 3) Cacheable parse -> reuse lastInfo if available
					// To avoid step 3 reusing a faulty 'lastInfo' from step 1, clear it in step 2.
					lastInfo = null;

					if (parsed.captures != null && parsed.captures.length() > 0) {
						bodyBuilder.put("Stdout messages during parsing:");
						for (Object obj : parsed.captures) {
							bodyBuilder.put(obj);
						}
					} else {
						bodyBuilder.put("No messages printed to stdout/stderr during parsing.");
						bodyBuilder.put("Look at your terminal for more information.");
					}
				}
			});
		} finally {
			final File tmpFile = tmp.get();
			if (tmpFile != null) {
				tmpFile.delete();
			}
		}

		// Somehow extract syntax errors from stdout?
		retBuilder.put("body", bodyBuilder);
		retBuilder.put("errors", errors != null ? errors : new JSONArray());
		retBuilder.put("totalTime", (System.nanoTime() - requestStart));
		retBuilder.put("createLocatorTime", BenchmarkTimer.CREATE_LOCATOR.getAccumulatedNano());
		retBuilder.put("applyLocatorTime", BenchmarkTimer.APPLY_LOCATOR.getAccumulatedNano());
		retBuilder.put("attrEvalTime", BenchmarkTimer.EVALUATE_ATTR.getAccumulatedNano());
		retBuilder.put("nodesAtPositionTime", BenchmarkTimer.LIST_NODES.getAccumulatedNano());
		retBuilder.put("pastaAttrsTime", BenchmarkTimer.LIST_PROPERTIES.getAccumulatedNano());

		System.out.println("Request done");
		return retBuilder;
	}

}
