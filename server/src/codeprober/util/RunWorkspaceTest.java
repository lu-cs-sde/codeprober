package codeprober.util;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.json.JSONObject;

import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.metaprogramming.StreamInterceptor.OtherThreadDataHandling;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.data.AsyncRequestReq;
import codeprober.protocol.data.AsyncRequestRes;
import codeprober.protocol.data.Decoration;
import codeprober.protocol.data.GetDecorationsReq;
import codeprober.protocol.data.GetDecorationsRes;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.WorkerTaskDone;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;
import codeprober.textprobe.TextProbeEnvironment;

public class RunWorkspaceTest {
	public static enum MergedResult {
		ALL_PASS, SOME_FAIL,
	}

	private final JsonRequestHandler dreqHandler;
	private final WorkspaceHandler wsHandler;
	final AtomicInteger numPass = new AtomicInteger();
	final AtomicInteger numFail = new AtomicInteger();
	private final ExecutorService concurrentExecutor;
	private final AtomicLong jobIdgenerator = new AtomicLong();

	public RunWorkspaceTest(JsonRequestHandler dreqHandler, WorkspaceHandler wsHandler, int numWorkerProcesses) {
		this.dreqHandler = dreqHandler;
		this.wsHandler = wsHandler;
		if (numWorkerProcesses >= 1) {
			// Each thread sends messages sequentially. Therefore, if #threads==#workers,
			// then there would be downtime while the worker waits for the next message from
			// the thread. Fix this by preparing for more threads than workers.
			this.concurrentExecutor = Executors.newFixedThreadPool((int) Math.ceil(numWorkerProcesses * 1.5));
		} else {
			this.concurrentExecutor = null;
		}
	}

	public static MergedResult run(JsonRequestHandler dreqHandler, WorkspaceHandler wsHandler, int numWorkerProcesses) {
		return new RunWorkspaceTest(dreqHandler, wsHandler, numWorkerProcesses).run();
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
			if (concurrentExecutor != null) {
				concurrentExecutor.shutdown();
				try {
					concurrentExecutor.awaitTermination(10_000L * 365L, TimeUnit.DAYS);
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
		System.out.println("Done: " + numPass + " pass, " + numFail + " fail");
		return (numFail.get() > 0) ? MergedResult.SOME_FAIL : MergedResult.ALL_PASS;
	}

	private void runDirectory(String workspacePath, Consumer<String> println) {
		final ListWorkspaceDirectoryRes listRes = wsHandler
				.handleListWorkspaceDirectory(new ListWorkspaceDirectoryReq(workspacePath));
		if (listRes.entries == null) {
			System.err.println("Invalid path in workspace: " + workspacePath);
			return;
		}

		final String parentPath = workspacePath == null ? "" : (workspacePath + "/");
		for (WorkspaceEntry e : listRes.entries) {
			switch (e.type) {
			case directory: {
				runDirectory(parentPath + e.asDirectory(), println);
				break;
			}
			case file: {
				final String fullPath = parentPath + e.asFile().name;

				final Runnable runFile = () -> {
					// TODO add flag to make expected values get printed too (expected + actual)
					final AsyncRequestReq asyncReq = new AsyncRequestReq(
							new GetDecorationsReq(TextProbeEnvironment.createParsingRequestData(fullPath), null, true)
									.toJSON(),
							jobIdgenerator.incrementAndGet());

					final JSONObject[] resPtr = new JSONObject[1];
					final CountDownLatch cdl = new CountDownLatch(1);
					final AsyncRequestRes immediateResp = AsyncRequestRes
							.fromJSON(dreqHandler.handleRequest(new ClientRequest(asyncReq.toJSON(), update -> {
								switch (update.value.type) {
								case workerTaskDone: {
									final WorkerTaskDone wtd = update.value.asWorkerTaskDone();
									if (wtd.isNormal()) {
										resPtr[0] = AsyncRequestRes.fromJSON(wtd.asNormal()).response.asSync();
									}
									cdl.countDown();
									break;
								}
								default: {
									// ignore
								}
								}
							}, new AtomicBoolean(true), p -> {
							})));

					if (immediateResp.response.isSync()) {
						resPtr[0] = immediateResp.response.asSync();
						cdl.countDown();
					}
					synchronized (cdl) {
						try {
							cdl.await();
						} catch (InterruptedException e1) {
							System.err.println("Interrupted while waiting for " + fullPath);
							e1.printStackTrace();
						}
					}
					final GetDecorationsRes res = resPtr[0] == null ? null : GetDecorationsRes.fromJSON(resPtr[0]);
					if (res == null || (res.didFailParsingFile != null && res.didFailParsingFile)) {
						println.accept("  ❌ " + fullPath + " - Failed parsing file");
						numFail.incrementAndGet();
						return;
					}

					if (res.lines == null) {
						if (Util.verbose) {
							println.accept("Nothing in file " + fullPath + "..");
						}
						return;
					}

					int localNumPass = 0;
					int localNumFail = 0;
					StringBuilder errMsgs = new StringBuilder();
					for (Decoration dec : res.lines) {
						switch (dec.type) {
						case "query":
						case "ok":
							++localNumPass;
							break;

						case "error":
							++localNumFail;
							String[] parts = dec.message.split("\n");
							int start = dec.contextStart != null ? dec.contextStart : dec.start;
							int end = dec.contextEnd != null ? dec.contextEnd : dec.start;
							String prefix = String.format("[%d:%d->%d:%d]", start >> 12, start & 0xFFF, end >>> 12,
									end & 0xFFF);
							parts[0] = String.format("%s %s", prefix, parts[0]);
							StringBuilder prefixSpacing = new StringBuilder();
							for (int i = 0; i <= prefix.length(); ++i) {
								prefixSpacing.append(' ');
							}
							for (int i = 1; i < parts.length; ++i) {
								parts[i] = prefixSpacing + parts[i];
							}
							errMsgs.append(Arrays.asList(parts).stream().map(x -> "\n     " + x)
									.collect(Collectors.joining("")));
							break;
						}
					}
					numPass.getAndAdd(localNumPass);
					numFail.getAndAdd(localNumFail);

					if (localNumPass == 0 && localNumFail == 0) {
						if (Util.verbose) {
							println.accept("Nothing in file " + fullPath + "..");
						}
						return;
					}
					if (localNumFail == 0) {
						println.accept("  ✅ " + fullPath);
					} else {
						println.accept("  ❌ " + fullPath + errMsgs.toString());
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
