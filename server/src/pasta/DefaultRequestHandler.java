package pasta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONObject;

import pasta.ast.AstNode;
import pasta.locator.ApplyLocator;
import pasta.locator.ApplyLocator.ResolvedNode;
import pasta.locator.AttrsInNode;
import pasta.locator.NodesAtPosition;
import pasta.metaprogramming.InvokeProblem;
import pasta.metaprogramming.PositionRepresentation;
import pasta.metaprogramming.Reflect;
import pasta.metaprogramming.StdIoInterceptor;
import pasta.protocol.AstCacheStrategy;
import pasta.protocol.ParameterValue;
import pasta.protocol.PositionRecoveryStrategy;
import pasta.protocol.create.EncodeResponseValue;
import pasta.protocol.decode.DecodeValue;
import pasta.rpc.JsonRequestHandler;
import pasta.util.ASTProvider;
import pasta.util.BenchmarkTimer;
import pasta.util.MagicStdoutMessageParser;

public class DefaultRequestHandler implements JsonRequestHandler {

	private final String underlyingCompilerJar;
	private final String[] forwardArgs;

	private AstInfo lastInfo = null;
	private String lastParsedInput = null;

	public DefaultRequestHandler(String underlyingJarFile, String[] forwardArgs) {
		this.underlyingCompilerJar = underlyingJarFile;
		this.forwardArgs = forwardArgs;
	}

	void handleParsedAst(Object ast, Function<String, Class<?>> loadAstClass, JSONObject queryObj,
			JSONObject retBuilder, JSONArray bodyBuilder) {
		if (ast == null) {
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

		PositionRepresentation positionRepresentation = null;
		try {
			if (Reflect.invoke0(ast, "getStartLine") instanceof Integer) {
				positionRepresentation = PositionRepresentation.SEPARATE_LINE_COLUMN;
			}
		} catch (RuntimeException e) {
			// Not getStartLine..
		}
		if (positionRepresentation == null) {
			if (Reflect.invoke0(ast, "getStart") instanceof Integer) {
				positionRepresentation = PositionRepresentation.PACKED_BITS;
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
				loadAstClass);
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
		case "pasta_spansAndNodeTypes": {
			final int rootStart = locator.getJSONObject("result").getInt("start");
			final int rootEnd = locator.getJSONObject("result").getInt("end");
			retBuilder.put("spansAndNodeTypes", new JSONArray(NodesAtPosition.get( //
					info, match.node, rootStart + (rootEnd - rootStart) / 2 //
			)));
			return;
		}
		case "pasta_pastaAttrs": {
			retBuilder.put("pastaAttrs",
					AttrsInNode.get(info, match.node, AttrsInNode.extractFilter(info, match.node)));
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
				if (args == null) {
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
						final ParameterValue param = DecodeValue.decode(info, args.getJSONObject(i));
						if (param == null) {
							bodyBuilder.put("Failed decoding parameter " + i);
							if (!captureStdio) {
								bodyBuilder.put("Click 'Capture stdio' to see more information.");
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
				}
				EncodeResponseValue.encode(info, bodyBuilder, value, new HashSet<>());
				retBuilder.put("args", updatedArgs);
			} catch (InvokeProblem e) {
				final Throwable cause = e.getCause();
				if (cause instanceof NoSuchMethodException) {
					bodyBuilder.put("No such attribute '" + queryAttrName + "' on "
							+ match.node.underlyingAstNode.getClass().getSimpleName());
				} else {
					if (cause != null && cause.getCause() != null) {
						cause.getCause().printStackTrace();
					} else {
						(cause != null ? cause : e).printStackTrace();
					}
					bodyBuilder.put("Exception thrown while evaluating attribute.");
					if (!captureStdio) {
						bodyBuilder.put("Click 'Capture stdio' to see full error.");
					}
				}
			}
		};

		if (captureStdio) {
			bodyBuilder.putAll(StdIoInterceptor.performCaptured((stdout, line) -> {
				JSONObject fmt = new JSONObject();
				fmt.put("type", stdout ? "stdout" : "stderr");
				fmt.put("value", line);
				return fmt;
			}, evaluateAttr));
		} else {
			evaluateAttr.run();
		}
	}

	@Override
	public JSONObject handleRequest(JSONObject queryObj) {

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
		BenchmarkTimer.APPLY_LOCATOR.reset();
		BenchmarkTimer.CREATE_LOCATOR.reset();
		BenchmarkTimer.EVALUATE_ATTR.reset();
		final JSONArray errors;

		final AtomicReference<File> tmp = new AtomicReference<>(null);
		try {
			errors = StdIoInterceptor.performCaptured(MagicStdoutMessageParser::parse, () -> {
				final String inputText = queryObj.getString("text");

				if (cacheStrategy.canCacheAST() //
						&& lastParsedInput != null //
						&& lastInfo != null //
						&& lastParsedInput.equals(inputText) //
						&& ASTProvider.hasUnchangedJar(underlyingCompilerJar)) {
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

				final File tmpFile;
				try {
					tmpFile = File.createTempFile("pasta-server", ".java");
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
				tmp.set(tmpFile);
				try {
					Files.write(tmpFile.toPath(), inputText.getBytes(StandardCharsets.UTF_8),
							StandardOpenOption.CREATE);
				} catch (IOException e) {
					System.out.println("Failed while copying source text to disk");
					e.printStackTrace();
					throw new RuntimeException(e);
				}

				final String[] astArgs = new String[1 + forwardArgs.length];
//				astArgs[0] = tmp.getAbsolutePath();
				System.arraycopy(forwardArgs, 0, astArgs, 0, forwardArgs.length);
				astArgs[forwardArgs.length] = tmpFile.getAbsolutePath();
//				System.out.println("fwd args: " + Arrays.toString(astArgs));

				final long parseStart = System.nanoTime();
				final boolean parsed = ASTProvider.parseAst(underlyingCompilerJar, astArgs, (ast, loadCls) -> {
					retBuilder.put("parseTime", (System.nanoTime() - parseStart));
					handleParsedAst(ast, loadCls, queryObj, retBuilder, bodyBuilder);
				});
				if (!parsed) {
					System.out.println("Parsing failed..");
				}
				if (!parsed && bodyBuilder.length() == 0) {
					bodyBuilder.put("🍝 Probe error");
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

		System.out.println("Request done");
		return retBuilder;
	}
}