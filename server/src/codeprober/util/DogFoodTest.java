package codeprober.util;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.metaprogramming.StreamInterceptor.OtherThreadDataHandling;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.requesthandler.LazyParser;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;
import codeprober.textprobe.DogEnvironment;
import codeprober.textprobe.DogEnvironment.ErrorMessage;
import codeprober.textprobe.Parser;
import codeprober.textprobe.ast.Container;
import codeprober.textprobe.ast.Document;
import codeprober.textprobe.ast.Probe;
import codeprober.textprobe.ast.Probe.Type;
import codeprober.textprobe.ast.Query;

public class DogFoodTest {
	public static enum MergedResult {
		ALL_PASS, SOME_FAIL,
	}

	private JsonRequestHandler requestHandler;
	final AtomicInteger numPass = new AtomicInteger();
	final AtomicInteger numFail = new AtomicInteger();
	final WorkspaceHandler workspaceHandler;

	private static boolean verbose = "true".equals(System.getProperty("cpr.verbose"));
	private final ExecutorService concurrentExecutor;

	private DogFoodTest(JsonRequestHandler requestHandler, int numWorkerProcesses, WorkspaceHandler workspaceHandler) {
		this.requestHandler = requestHandler;
		if (numWorkerProcesses >= 1) {
			// Each thread sends messages sequentially. Therefore, if #threads==#workers,
			// then there would be downtime while the worker waits for the next message from
			// the thread. Fix this by preparing for more threads than workers.
			this.concurrentExecutor = Executors.newFixedThreadPool(numWorkerProcesses * 2);
		} else {
			this.concurrentExecutor = null;
		}
		this.workspaceHandler = workspaceHandler;
	}

	public static MergedResult run(JsonRequestHandler requestHandler, int numConcurrentThreads) {
		return run(requestHandler, numConcurrentThreads, WorkspaceHandler.getDefault());
	}

	public static MergedResult run(JsonRequestHandler requestHandler, int numConcurrentThreads,
			WorkspaceHandler handler) {
		// Use the API (WorkspaceHandler) to mimic Codeprober client behavior as close
		// as possible
		final DogFoodTest rwt = new DogFoodTest(requestHandler, numConcurrentThreads, handler);

		final PrintStream out = System.out;
		final Consumer<String> println = line -> out.println(line);
		StdIoInterceptor interceptor = null;
		if (!verbose) {
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
			rwt.runDirectory(null, println);
			if (rwt.concurrentExecutor != null) {
				try {
					rwt.concurrentExecutor.shutdown();
					rwt.concurrentExecutor.awaitTermination(/* Illidan be like */ 10_000L * 365L, TimeUnit.DAYS);
				} catch (InterruptedException e) {
					System.err.println("Interrupted while waiting for concurrent tests to finish");
					e.printStackTrace();
					return MergedResult.SOME_FAIL;
				}
			}
		} finally {
			if (interceptor != null) {
				interceptor.restore();
			}
		}
		System.out.println("Done: " + rwt.numPass + " pass, " + rwt.numFail + " fail");
		return (rwt.numFail.get() > 0) ? MergedResult.SOME_FAIL : MergedResult.ALL_PASS;
	}

	private void runDirectory(String workspacePath, Consumer<String> println) {
		final ListWorkspaceDirectoryRes res = workspaceHandler
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
				final int[] localNumPass = new int[1];
				final int[] localNumFail = new int[1];
				final String fullPath = parentPath + e.asFile().name;

				final Runnable runFile = () -> {
					final String src = LazyParser.extractText(ParsingSource.fromWorkspacePath(fullPath),
							workspaceHandler);
					final Document doc = Parser.parse(src, '[', ']');
					// First check the static semantics of the text probes themselves
					final Set<String> problems = doc.problems();
					println.accept("in " + fullPath + ", found " + doc.containers.getNumChild()
							+ " children; problems: " + problems);
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
					// Else no problems -> time to actually evaluate the queries
					final DogEnvironment env = new DogEnvironment(requestHandler, workspaceHandler,
							ParsingSource.fromWorkspacePath(fullPath), doc, null, concurrentExecutor != null);
					env.loadVariables();
					localNumFail[0] += env.errMsgs.size();

					for (Container c : doc.containers) {
						Probe p = c.probe();
						if (p == null) {
							continue;
						}
						if (p.type == Type.QUERY) {
							final Query q = p.asQuery();
							final int preErrSize = env.errMsgs.size();
							final List<RpcBodyLine> lhs = env.evaluateQuery(q);
							if (lhs != null) {
								if (q.assertion.isPresent()) {
									if (env.evaluateComparison(q, lhs)) {
										++localNumPass[0];
									}
								} else {
									// Not an assert, it is a success by virtue of lhs being non-null.
									++localNumPass[0];
								}
							}
							if (env.errMsgs.size() != preErrSize || lhs == null) {
								++localNumFail[0];
							}
						}
					}
//					println.accept("In " + fullPath + ", got pass/fail: " + localNumPass[0] + "/" + localNumFail[0]);
//					doc.containers.forEach(con -> {
//						println.accept(con.loc()+": " + (con.probe() == null ? "<no probe>" : con.probe().pp()));
//					});

					numFail.addAndGet(localNumFail[0]);
					numPass.addAndGet(localNumPass[0]);

					if (localNumPass[0] == 0 && localNumFail[0] == 0) {
						// No tests, don't output anything
						if (verbose) {
							println.accept("Nothing in file " + fullPath + "..");
						}
					} else if (localNumFail[0] == 0) {
						println.accept("  ✅ " + localNumPass[0] + " " + fullPath);
					} else {
						final StringBuilder msg = new StringBuilder();
						msg.append("  ❌ " + fullPath);
//						for (String errMsg : env.errMsgs) {
//							msg.append("\n     " + errMsg);
//						}
						for (ErrorMessage errMsg : env.errMsgs) {
							msg.append(Arrays.asList(errMsg.toString().split("\n")).stream().map(x -> "\n     " + x)
									.collect(Collectors.joining("")));
						}
						println.accept(msg.toString());
					}
				};
				if (concurrentExecutor == null) {
					runFile.run();
				} else {
					concurrentExecutor.submit(runFile);
				}
			}
			}
		}
	}
}
