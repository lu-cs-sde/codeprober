package pasta;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONObject;

import pasta.locator.ApplyLocator;
import pasta.locator.ApplyLocator.ResolvedNode;
import pasta.locator.AttrsInNode;
import pasta.locator.NodesAtPosition;
import pasta.metaprogramming.Reflect;
import pasta.metaprogramming.StdIoInterceptor;
import pasta.protocol.ParameterValue;
import pasta.protocol.PositionRecoveryStrategy;
import pasta.protocol.create.EncodeResponseValue;
import pasta.protocol.decode.DecodeValue;
import pasta.rpc.JsonRequestHandler;
import pasta.util.ASTProvider;
import pasta.util.MagicStdoutMessageParser;

public class DefaultRequestHandler implements JsonRequestHandler {

	private final String underlyingCompilerJar;
	private final String[] forwardArgs;

	public DefaultRequestHandler(String underlyingJarFile, String[] forwardArgs) {
		this.underlyingCompilerJar = underlyingJarFile;
		this.forwardArgs = forwardArgs;
	}

	private void handleParsedAst(Object ast, Function<String, Class<?>> loadAstClass, JSONObject queryObj,
			JSONObject retBuilder, JSONArray bodyBuilder) {
		if (ast == null) {
			bodyBuilder.put("Compiler exited normally, but no 'DrAST_root_node' found");
			bodyBuilder.put("If you call System.exit(<not zero>) when parsing fails then this message will disappear.");
			return;
		}
		System.out.println("Parsed, got: " + ast);

		final AstInfo info = new AstInfo(ast, PositionRecoveryStrategy.fallbackParse(queryObj.getString("posRecovery")),
				loadAstClass);

		final JSONObject queryBody = queryObj.getJSONObject("query");
		final JSONObject locator = queryBody.getJSONObject("locator");
		ResolvedNode match = ApplyLocator.toNode(info, locator);

		System.out.println("MatchedNode: " + match);
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
			final int rootStart = locator.getJSONObject("root").getInt("start");
			final int rootEnd = locator.getJSONObject("root").getInt("end");
			retBuilder.put("spansAndNodeTypes", new JSONArray(NodesAtPosition.get( //
					match.node, rootStart + (rootEnd - rootStart) / 2, info.recoveryStrategy //
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
				if (args == null) {
					value = Reflect.throwingInvoke0(match.node, queryAttrName);
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
						argValues[i] = param.value;
					}
					value = Reflect.invokeN(match.node, queryAttrName, argTypes, argValues);
				}
				EncodeResponseValue.encode(info, bodyBuilder, value, new HashSet<>());
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
				bodyBuilder
						.put("No such attribute '" + queryAttrName + "' on " + match.node.getClass().getSimpleName());
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
		System.out.println("Incoming query: " + queryObj.toString(2));

		// Root response object
		final JSONObject retBuilder = new JSONObject();

		// The "body" of the probe, can be seen as 'stdout'. Add information here that
		// the user wants to see.
		// Meta-information goes in the 'root' response object.
		final JSONArray bodyBuilder = new JSONArray();

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
		} catch (IOException e) {
			System.out.println("Failed while copying source text to disk");
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		final JSONArray errors;
		try {
			errors = StdIoInterceptor.performCaptured(MagicStdoutMessageParser::parse, () -> {
				final String[] astArgs = new String[1 + forwardArgs.length];
				astArgs[0] = tmp.getAbsolutePath();
				System.arraycopy(forwardArgs, 0, astArgs, 1, forwardArgs.length);
				System.out.println("fwd args: " + Arrays.toString(astArgs));

				final boolean parsed = ASTProvider.parseAst(underlyingCompilerJar, astArgs,
						(ast, loadCls) -> handleParsedAst(ast, loadCls, queryObj, retBuilder, bodyBuilder));
				if (!parsed) {
					System.out.println("Parsing failed..");
				}
				if (!parsed && bodyBuilder.length() == 0) {
					bodyBuilder.put("🍝 Probe error");
				}
			});
		} finally {
			tmp.delete();
		}

		// Somehow extract syntax errors from stdout?
		retBuilder.put("body", bodyBuilder);
		retBuilder.put("errors", errors != null ? errors : new JSONArray());

		return retBuilder;
	}
}
