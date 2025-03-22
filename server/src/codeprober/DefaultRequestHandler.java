package codeprober;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.json.JSONException;
import org.json.JSONObject;

import codeprober.ast.AstNode;
import codeprober.metaprogramming.AstNodeApiStyle;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.metaprogramming.TypeIdentificationStyle;
import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.data.CompleteReq;
import codeprober.protocol.data.CompleteRes;
import codeprober.protocol.data.EvaluatePropertyReq;
import codeprober.protocol.data.EvaluatePropertyRes;
import codeprober.protocol.data.GetTestSuiteReq;
import codeprober.protocol.data.GetTestSuiteRes;
import codeprober.protocol.data.GetWorkspaceFileReq;
import codeprober.protocol.data.GetWorkspaceFileRes;
import codeprober.protocol.data.HoverReq;
import codeprober.protocol.data.HoverRes;
import codeprober.protocol.data.ListNodesReq;
import codeprober.protocol.data.ListNodesRes;
import codeprober.protocol.data.ListPropertiesReq;
import codeprober.protocol.data.ListPropertiesRes;
import codeprober.protocol.data.ListTestSuitesReq;
import codeprober.protocol.data.ListTestSuitesRes;
import codeprober.protocol.data.ListTreeReq;
import codeprober.protocol.data.ListTreeRes;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.PutTestSuiteReq;
import codeprober.protocol.data.PutTestSuiteRes;
import codeprober.protocol.data.PutWorkspaceContentReq;
import codeprober.protocol.data.PutWorkspaceContentRes;
import codeprober.protocol.data.PutWorkspaceMetadataReq;
import codeprober.protocol.data.PutWorkspaceMetadataRes;
import codeprober.protocol.data.RenameWorkspacePathReq;
import codeprober.protocol.data.RenameWorkspacePathRes;
import codeprober.protocol.data.RequestAdapter;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.UnlinkWorkspacePathReq;
import codeprober.protocol.data.UnlinkWorkspacePathRes;
import codeprober.requesthandler.CompleteHandler;
import codeprober.requesthandler.EvaluatePropertyHandler;
import codeprober.requesthandler.HoverHandler;
import codeprober.requesthandler.LazyParser;
import codeprober.requesthandler.ListNodesHandler;
import codeprober.requesthandler.ListPropertiesHandler;
import codeprober.requesthandler.ListTreeRequestHandler;
import codeprober.requesthandler.TestRequestHandler;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;
import codeprober.server.BackingFileSettings;
import codeprober.toolglue.ParseResult;
import codeprober.toolglue.UnderlyingTool;
import codeprober.util.ASTProvider;
import codeprober.util.SessionLogger;

public class DefaultRequestHandler implements JsonRequestHandler {

	private final UnderlyingTool underlyingTool;
	private final String[] defaultForwardArgs;
	private final SessionLogger logger;

	private AstInfo lastInfo = null;
	private ParsingSource lastParsedInput = null;
	private String[] lastForwardArgs;
	private Long lastToolVersionId;

	private KnownFileData lastParsedWorkspaceInput;

	public DefaultRequestHandler(UnderlyingTool underlyingTool) {
		this(underlyingTool, null, null);
	}

	public DefaultRequestHandler(UnderlyingTool underlyingTool, String[] forwardArgs, SessionLogger logger) {
		this.underlyingTool = underlyingTool;
		this.defaultForwardArgs = forwardArgs != null ? forwardArgs : new String[0];
		this.lastForwardArgs = this.defaultForwardArgs;
		this.logger = logger;
	}

	private AstInfo parsedAstToInfo(Object ast, PositionRecoveryStrategy posRecovery) {
		AstNode astNode = new AstNode(ast);

		AstNodeApiStyle positionRepresentation = null;
		try {
			if (Reflect.invoke0(ast, "cpr_getStartLine") instanceof Integer) {
				positionRepresentation = AstNodeApiStyle.CPR_SEPARATE_LINE_COLUMN;
			}
		} catch (RuntimeException e) {
			// Not cpr_getStartLine..
		}
		if (positionRepresentation == null) {
			try {
				if (Reflect.invoke0(ast, "getStartLine") instanceof Integer) {
					positionRepresentation = AstNodeApiStyle.JASTADD_SEPARATE_LINE_COLUMN;
				}
			} catch (RuntimeException e) {
				// Not getStartLine..
			}
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
			try {

				if (Reflect.invoke0(ast, "getStart") instanceof Integer) {
					positionRepresentation = AstNodeApiStyle.BEAVER_PACKED_BITS;
				}
			} catch (RuntimeException e) {
				// Not packed bits
			}
		}
		if (positionRepresentation == null) {
			try {
				if (Reflect.invoke0(ast, "getNumChild") instanceof Integer) {
					// Fall back to no position
					positionRepresentation = AstNodeApiStyle.JASTADD_NO_POSITION;
				}
			} catch (RuntimeException e) {
				// Not packed bits
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

		final AstInfo info = new AstInfo(astNode, posRecovery, positionRepresentation,
				TypeIdentificationStyle.parse(System.getProperty("CPR.TYPE_IDENTIFICATION_STYLE")));
		lastInfo = info;
		return info;
	}

	@Override
	public JSONObject handleRequest(ClientRequest request) {
		final AtomicReference<File> tmp = new AtomicReference<>(null);
		final BiFunction<String, String, File> createTmpFile = (inputText, tmpSuffix) -> {
			final File existing = tmp.get();
			if (existing != null) {
				return existing;
			}
			final File backingFile = BackingFileSettings.getRealFileToBeUsedInRequests();
			if (backingFile != null) {
				try {
					BackingFileSettings.write(inputText);
					// Do NOT set 'tmp' to backingFile, as that would cause it to be deleted.
					return backingFile;
				} catch (IOException e) {
					System.out.println("Failed while copying source text to the backing file " + backingFile);
					e.printStackTrace();
					throw new RuntimeException(e);

				}
			}
			try {
				final File tmpFile = File.createTempFile("code-prober", tmpSuffix);
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
			final String slowdown = System.getenv("SIMULATED_SLOWDOWN_MS");
			if (slowdown != null) {
				try {
					Thread.sleep(Integer.parseInt(slowdown));
				} catch (InterruptedException | NumberFormatException e) {
					System.out.println("Interrupted while performing simulated slowdown");
					e.printStackTrace();
				}
			}

			final LazyParser lp = new LazyParser() {

				@Override
				public ParsedAst parse(ParsingSource src, AstCacheStrategy cacheStrategy, List<String> mainArgs,
						PositionRecoveryStrategy posRecovery, String tmpFileSuffix) {
					final ParseResultWithExtraInfo res = doParse(src,
							cacheStrategy != null ? cacheStrategy.name() : null, mainArgs, tmpFileSuffix,
							createTmpFile);
					if (res == null) {
						return new ParsedAst(null);
					}
					if (res.rootNode == null) {
						return new ParsedAst(null, res.parseTime, res.captures);
					}
					return new ParsedAst(parsedAstToInfo(res.rootNode, posRecovery), res.parseTime, res.captures);
				}

				@Override
				public void discardCachedAst() {
					lastParsedInput = null;
				}
			};
			final AtomicBoolean addLog = new AtomicBoolean(logger != null);
			final JSONObject handled = new RequestAdapter() {

				@Override
				protected ListTreeRes handleListTree(ListTreeReq req) {
					return ListTreeRequestHandler.apply(req, lp);
				}

				@Override
				protected ListNodesRes handleListNodes(ListNodesReq req) {
					if (addLog.getAndSet(false)) {
						logger.log(new JSONObject() //
								.put("t", "ListNodes") //
								.put("pos", req.pos));
					}
					return ListNodesHandler.apply(req, lp);
				}

				@Override
				protected ListPropertiesRes handleListProperties(ListPropertiesReq req) {
					if (addLog.getAndSet(false)) {
						logger.log(new JSONObject() //
								.put("t", "ListProperties") //
								.put("node", req.locator.result.type));
					}
					return ListPropertiesHandler.apply(req, lp);
				}

				@Override
				protected ListTestSuitesRes handleListTestSuites(ListTestSuitesReq req) {
					return TestRequestHandler.list(req);
				}

				@Override
				protected GetTestSuiteRes handleGetTestSuite(GetTestSuiteReq req) {
					return TestRequestHandler.get(req);
				}

				@Override
				protected PutTestSuiteRes handlePutTestSuite(PutTestSuiteReq req) {
					return TestRequestHandler.put(req);
				}

				@Override
				protected EvaluatePropertyRes handleEvaluateProperty(EvaluatePropertyReq req) {
					if (addLog.getAndSet(false)) {
						logger.log(new JSONObject() //
								.put("t", "EvaluateProperty") //
								.put("prop", req.property.name) //
								.put("node", req.locator.result.type));
					}
					return EvaluatePropertyHandler.apply(req, lp);
				}

				@Override
				protected HoverRes handleHover(HoverReq req) {
					return HoverHandler.apply(req, lp);
				}

				@Override
				protected CompleteRes handleComplete(CompleteReq req) {
					return CompleteHandler.apply(req, lp);
				}

				@Override
				protected GetWorkspaceFileRes handleGetWorkspaceFile(GetWorkspaceFileReq req) {
					return WorkspaceHandler.handleGetWorkspaceFile(req);
				};

				@Override
				protected ListWorkspaceDirectoryRes handleListWorkspaceDirectory(ListWorkspaceDirectoryReq req) {
					return WorkspaceHandler.handleListWorkspaceDirectory(req);
				};

				@Override
				protected PutWorkspaceContentRes handlePutWorkspaceContent(PutWorkspaceContentReq req) {
					return WorkspaceHandler.handlePutWorkspaceContent(req);
				};

				@Override
				protected PutWorkspaceMetadataRes handlePutWorkspaceMetadata(PutWorkspaceMetadataReq req) {
					return WorkspaceHandler.handlePutWorkspaceMetadata(req);
				};

				@Override
				protected RenameWorkspacePathRes handleRenameWorkspacePath(RenameWorkspacePathReq req) {
					return WorkspaceHandler.handleRenameWorkspacePath(req);
				};

				protected UnlinkWorkspacePathRes handleUnlinkWorkspacePath(UnlinkWorkspacePathReq req) {
					return WorkspaceHandler.handleUnlinkWorkspacePath(req);

				};
			}.handle(request.data);
			if (addLog.getAndSet(false)) {
				final String type = request.data.optString("type");
				if (type != null) {
					logger.log(new JSONObject().put("t", type));
				}
			}
			if (handled != null) {
				return handled;
			}
			throw new JSONException("Unexpected request type");
//		} catch (JSONException e) {
//			System.err.println("Failed handling typed request");
//			e.printStackTrace();
			// Fall down to old implementation, if available
		} finally {
			final File tmpFile = tmp.get();
			if (tmpFile != null) {
				tmpFile.delete();
			}
		}
	}

	private boolean parsingSourceIsEqualToLast(ParsingSource newSrc) {
		if (lastParsedInput == null) {
			return newSrc == null;
		}
		if (lastParsedInput.type != newSrc.type) {
			return false;
		}
		switch (lastParsedInput.type) {
		case text:
			return lastParsedInput.asText().equals(newSrc.asText());

		case workspacePath:
			final String p1Path = lastParsedInput.asWorkspacePath();
			final String p2Path = newSrc.asWorkspacePath();
			if (!p1Path.equals(p2Path)) {
				return false;
			}
			final File wsFile = WorkspaceHandler.getWorkspaceFile(p1Path);
			if (wsFile == null) {
				// Both point to the same (nonexisting) file -> equal
				return true;
			}

			if (lastParsedWorkspaceInput == null) {
				System.err.println("?? There should be workspace input info since last input is workspace");
				return false;
			}
			KnownFileData freshStat = statFile(wsFile);
			return freshStat.equals(lastParsedWorkspaceInput);
		default: {
			System.err.println("Unknown ParsingSource type: " + lastParsedInput.type);
			return false;
		}
		}
	}

	private KnownFileData statFile(File f) {
		return new KnownFileData(f.lastModified(), f.length(), WorkspaceHandler.getWorkspaceFileWriteCounter(f));
	}

	private ParseResultWithExtraInfo doParse(final ParsingSource inputSource, String optCacheStrategyVal,
			List<String> optArgsOverrideVal, String tmpFileSuffix, BiFunction<String, String, File> createTmpFile) {

		final AstCacheStrategy cacheStrategy = AstCacheStrategy.fallbackParse(optCacheStrategyVal);
		if (cacheStrategy == AstCacheStrategy.PURGE) {
			ASTProvider.purgeCache();
			lastInfo = null;
		}

		final String[] fwdArgs;
		if (optArgsOverrideVal != null) {
			fwdArgs = optArgsOverrideVal.toArray(new String[optArgsOverrideVal.size()]);
		} else {
			fwdArgs = defaultForwardArgs;
		}

		final boolean newFwdArgs = !Arrays.equals(lastForwardArgs, fwdArgs);
		boolean maybeCacheAST = cacheStrategy.canCacheAST() //
				&& lastParsedInput != null //
				&& lastInfo != null //
				&& !newFwdArgs //
				&& lastToolVersionId != null && lastToolVersionId == underlyingTool.getVersionId();

		final Supplier<File> convertInputToFile = () -> {
			switch (inputSource.type) {
			case text: {
				return createTmpFile.apply(inputSource.asText(), tmpFileSuffix);
			}
			case workspacePath: {
				final File ret = WorkspaceHandler.getWorkspaceFile(inputSource.asWorkspacePath());
				if (ret != null) {
					lastParsedWorkspaceInput = statFile(ret);
				}
				return ret;
			}
			default: {
				System.err.println("Unknown input source type: " + inputSource.type);
				return null;
			}
			}
		};
		if (maybeCacheAST && !parsingSourceIsEqualToLast(inputSource)) {
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
					final File tmpFile = convertInputToFile.get();
					if (tmpFile == null) {
						System.err.println("Illegal input file");
						return null;
					}
					final long flushStart = System.nanoTime();
					final Boolean replacedOk = (Boolean) optimizedFlusher.invoke(lastInfo.ast.underlyingAstNode,
							tmpFile.getAbsolutePath());
					System.out.println("Tried optimized flush, result: " + replacedOk);
					if (replacedOk) {
						lastParsedInput = inputSource;
						return new ParseResultWithExtraInfo(lastInfo.ast.underlyingAstNode, null,
								System.nanoTime() - flushStart);
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
				return new ParseResultWithExtraInfo(lastInfo.ast.underlyingAstNode, null,
						System.nanoTime() - flushStart);
			} catch (InvokeProblem ip) {
				System.out.println("Problem when flushing previous tree");
				ip.printStackTrace();
				lastInfo = null;
			}
		}
		lastParsedInput = inputSource;
		lastToolVersionId = underlyingTool.getVersionId();

		final File tmpFile = convertInputToFile.get();
		if (tmpFile == null) {
			System.err.println("Illegal input file");
			return null;
		}
		final String[] astArgs = new String[1 + fwdArgs.length];
		System.arraycopy(fwdArgs, 0, astArgs, 0, fwdArgs.length);
		astArgs[fwdArgs.length] = tmpFile.getAbsolutePath();
		lastForwardArgs = fwdArgs;

		final long parseStart = System.nanoTime();
		final ParseResult parsed = underlyingTool.parse(astArgs);
		if (parsed.rootNode != null) {
			return new ParseResultWithExtraInfo(parsed.rootNode, parsed.captures, System.nanoTime() - parseStart);
		} else {
			final List<RpcBodyLine> captures = new ArrayList<>();
			captures.add(RpcBodyLine.fromPlain("Parsing failed."));

			// Consider this sequence of requests:
			// 1) Successful parse -> lastInfo set
			// 2) Failed parse
			// 3) Cacheable parse -> reuse lastInfo if available
			// To avoid step 3 reusing a faulty 'lastInfo' from step 1, clear it in step 2.
			lastInfo = null;

			if (parsed.captures != null && parsed.captures.size() > 0) {
				captures.add(RpcBodyLine.fromPlain("Extra information that may help diagnose the problem:"));
				captures.addAll(parsed.captures);
			} else {
				captures.add(RpcBodyLine.fromPlain("No messages printed to stdout/stderr during parsing."));
				captures.add(RpcBodyLine.fromPlain("Look at your terminal for more information."));
			}
			return new ParseResultWithExtraInfo(null, captures, System.nanoTime() - parseStart);
		}
//		});
	}

	private static class ParseResultWithExtraInfo {
		public final Object rootNode;
		public final long parseTime;
		public final List<RpcBodyLine> captures;

		public ParseResultWithExtraInfo(Object rootNode, List<RpcBodyLine> captures, long parseTime) {
			this.rootNode = rootNode;
			this.captures = captures != null ? captures : new ArrayList<>();
			this.parseTime = parseTime;
		}
	}

	private static class KnownFileData {
		private long lastModified;
		private long length;
		private int workspaceWriteCounter;

		public KnownFileData(long lastModified, long length, int workspaceWriteCounter) {
			this.lastModified = lastModified;
			this.length = length;
			this.workspaceWriteCounter = workspaceWriteCounter;
		}

		@Override
		public int hashCode() {
			return Objects.hash(lastModified, length, workspaceWriteCounter);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			KnownFileData other = (KnownFileData) obj;
			return lastModified == other.lastModified && length == other.length
					&& workspaceWriteCounter == other.workspaceWriteCounter;
		}

	}
}
