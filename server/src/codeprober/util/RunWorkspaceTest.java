package codeprober.util;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import codeprober.DefaultRequestHandler;
import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.metaprogramming.StreamInterceptor.OtherThreadDataHandling;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.requesthandler.LazyParser;
import codeprober.requesthandler.LazyParser.ParsedAst;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.textprobe.Parser;
import codeprober.textprobe.TextProbeEnvironment;
import codeprober.textprobe.TextProbeEnvironment.ErrorMessage;
import codeprober.textprobe.TextProbeEnvironment.QueryResult;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Document;
import codeprober.textprobe.ast.Probe;
import codeprober.textprobe.ast.Probe.Type;
import codeprober.textprobe.ast.Query;
import codeprober.toolglue.UnderlyingTool;

public class RunWorkspaceTest {
	public static enum MergedResult {
		ALL_PASS, SOME_FAIL,
	}

	private DefaultRequestHandler dreqHandler;
	final AtomicInteger numPass = new AtomicInteger();
	final AtomicInteger numFail = new AtomicInteger();

	public RunWorkspaceTest(UnderlyingTool tool, WorkspaceHandler workspaceHandler) {
		this(new DefaultRequestHandler(tool, workspaceHandler));
	}

	public RunWorkspaceTest(DefaultRequestHandler dreqHandler) {
		this.dreqHandler = dreqHandler;
	}

	public static MergedResult run(UnderlyingTool tool) {
		return new RunWorkspaceTest(tool, WorkspaceHandler.getDefault()).run();
	}

	public static MergedResult run(DefaultRequestHandler dreqHandler) {
		return new RunWorkspaceTest(dreqHandler).run();
	}

	public MergedResult run() {
		final PrintStream out = System.out;
		final Consumer<String> println = line -> out.println(line);
		StdIoInterceptor interceptor = null;
		if (!Util.verbose) {
			// Prevent messages during test runs
			interceptor = new StdIoInterceptor(false, OtherThreadDataHandling.MERGE) {
				@Override
				public void onLine(boolean stdout, String line) {
					// Noop
				}
			};
			interceptor.install();
		}
		try {
			runDirectory(null, println);
		} finally {
			if (interceptor != null) {
				interceptor.restore();
			}
		}
		System.out.println("Done: " + numPass + " pass, " + numFail + " fail");
		return (numFail.get() > 0) ? MergedResult.SOME_FAIL : MergedResult.ALL_PASS;
	}

	private void runDirectory(String workspacePath, Consumer<String> println) {
		final ListWorkspaceDirectoryRes res = dreqHandler.getWorkspaceHandler()
				.handleListWorkspaceDirectory(new ListWorkspaceDirectoryReq(workspacePath));
		if (res.entries == null) {
			System.err.println("Invalid path in workspace: " + workspacePath);
			return;
		}

		final String parentPath = workspacePath == null ? "" : (workspacePath + "/");
		for (WorkspaceEntry e : res.entries) {
			switch (e.type) {
			case directory: {
				runDirectory(parentPath + e.asDirectory(), println);
				break;
			}
			case file: {
				final String fullPath = parentPath + e.asFile().name;

				final Runnable runFile = () -> {
					final ParsingSource psrc = ParsingSource.fromWorkspacePath(fullPath);
					final String srcContents = LazyParser.extractText(psrc, dreqHandler.getWorkspaceHandler());
					final Document doc = Parser.parse(srcContents, '[', ']');
					if (doc.containers.isEmpty()) {
						if (Util.verbose) {
							println.accept("Nothing in file " + fullPath + "..");
						}
						return;
					}
					// First check the static semantics of the text probes themselves
					final Set<String> problems = doc.problems();
					if (!problems.isEmpty()) {
						numFail.addAndGet(problems.size());
						final StringBuilder msg = new StringBuilder();
						msg.append("  ❌ " + fullPath);
						for (String errMsg : problems) {
							msg.append(Arrays.asList(errMsg.split("\n")).stream().map(x -> "\n     " + x)
									.collect(Collectors.joining("")));
						}
						println.accept(msg.toString());
						return;
					}
					final ParsedAst parsedAst = dreqHandler.performParsedRequest(
							lp -> lp.parse(TextProbeEnvironment.createParsingRequestData(fullPath)));
					if (parsedAst.info == null) {
						numFail.incrementAndGet();
						println.accept("  ❌ " + fullPath + " - Failed parsing file");
					} else {

						// Else no problems -> time to actually evaluate the queries
						int localNumPass = 0;
						int localNumFail = 0;
						final TextProbeEnvironment env = new TextProbeEnvironment(parsedAst.info, doc);
						env.loadVariables();
						localNumFail += env.errMsgs.size();
						if (localNumFail == 0) {
							for (Container c : doc.containers) {
								Probe p = c.probe();
								if (p == null) {
									continue;
								}
								if (p.type == Type.QUERY) {
									final Query q = p.asQuery();
									final int preErrSize = env.errMsgs.size();
									final QueryResult lhs = env.evaluateQuery(q);
									if (lhs != null) {
										if (q.assertion.isPresent()) {
											if (env.evaluateComparison(q, lhs)) {
												++localNumPass;
											}
										} else {
											// Not an assert, it is a success by virtue of lhs being non-null.
											++localNumPass;
										}
									}
									if (env.errMsgs.size() != preErrSize || lhs == null) {
										++localNumFail;
									}
								}
							}
						}
						numFail.addAndGet(localNumFail);
						numPass.addAndGet(localNumPass);

						if (localNumPass == 0 && localNumFail == 0) {
							// No tests, don't output anything
							if (Util.verbose) {
								println.accept("Nothing in file " + fullPath + "..");
							}
						} else if (localNumFail == 0) {
							println.accept("  ✅ " + fullPath);
						} else {
							final StringBuilder msg = new StringBuilder();
							msg.append("  ❌ " + fullPath);
							for (ErrorMessage errMsg : env.errMsgs) {
								msg.append(Arrays.asList(errMsg.toString().split("\n")).stream().map(x -> "\n     " + x)
										.collect(Collectors.joining("")));
							}
							println.accept(msg.toString());
						}
					}
				};
				runFile.run();
			}
			}
		}
	}
}
