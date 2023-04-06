package codeprober;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.json.JSONObject;

import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.data.EvaluatePropertyReq;
import codeprober.protocol.data.EvaluatePropertyRes;
import codeprober.protocol.data.GetTestSuiteReq;
import codeprober.protocol.data.GetTestSuiteRes;
import codeprober.protocol.data.ListTestSuitesReq;
import codeprober.protocol.data.ListTestSuitesRes;
import codeprober.protocol.data.NestedTest;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.SynchronousEvaluationResult;
import codeprober.protocol.data.TestCase;
import codeprober.protocol.data.TestSuite;
import codeprober.protocol.data.WorkerTaskDone;
import codeprober.rpc.JsonRequestHandler;

public class TestClient {

	private final JsonRequestHandler requestHandler;
	private final AtomicLong jobIdGenerator = new AtomicLong();

	public TestClient(JsonRequestHandler requestHandler) {
		this.requestHandler = requestHandler;
	}

	private ClientRequest constructMessage(JSONObject query) {
		return new ClientRequest(query, obj -> {
		}, new AtomicBoolean(true));
	}

	public List<String> getTestSuites() {
		return ListTestSuitesRes.fromJSON(requestHandler.handleRequest( //
				constructMessage(new ListTestSuitesReq().toJSON()))).result.asSuites();
	}

	public TestSuite getTestSuiteContents(String suiteName) {
		return GetTestSuiteRes.fromJSON(requestHandler.handleRequest( //
				constructMessage(new GetTestSuiteReq(suiteName).toJSON()))).result.asContents();
	}

	private NodeLocator findLocatorWithNestingPath(List<Integer> path, int pathIndex, List<RpcBodyLine> lines) {
		final Integer step = path.get(pathIndex);
		if (step >= lines.size()) {
			return null;
		}
		final RpcBodyLine line = lines.get(step);
		if (pathIndex >= path.size() - 1) {
			return line.isNode() ? line.asNode() : null;
		}
		switch (line.type) {
		case arr:
			return findLocatorWithNestingPath(path, pathIndex + 1, line.asArr());
		default:
			return null;
		}
	}

	private List<RpcBodyLine> filterOutStdio(List<RpcBodyLine> lines) {
		final List<RpcBodyLine> ret = new ArrayList<>();
		for (RpcBodyLine line : lines) {
			switch (line.type) {
			case stdout:
			case stderr:
				continue;

			case arr:
				ret.add(RpcBodyLine.fromArr(filterOutStdio(line.asArr())));
				break;

			default:
				ret.add(line);
			}
		}
		return ret;
	}

//	private String toSetStringy(String prefix, RpcBodyLine line) {
//		switch (line.type) {
//		case arr:
//			final AtomicInteger idx = new AtomicInteger();
//			return line.asArr().stream().map(sub -> toSetStringy(prefix + "," + idx.getAndIncrement(), sub))
//					.collect(Collectors.joining(","));
//		default:
//			return line.toJSON().toString();
//		}
//	}

	private boolean compareSetLines(List<RpcBodyLine> expected, List<RpcBodyLine> actual) {
		final Function<List<RpcBodyLine>, List<String>> encode = x -> {
			final List<String> ret = x.stream().map(y -> {
				if (y.isArr()) {
					List<String> inner = new ArrayList<>();
					for (RpcBodyLine z : y.asArr()) {
						inner.add(z.toJSON().toString());
					}
					inner.sort(String::compareTo);
					return inner.stream().collect(Collectors.joining(","));
				} else {
					return y.toJSON().toString();
				}
			}).collect(Collectors.toList());
//			}
			ret.sort(String::compareTo);
			return ret;
		};

		final List<String> lhs = encode.apply(filterOutStdio(expected));
		final List<String> rhs = encode.apply(filterOutStdio(actual));
		return lhs.equals(rhs);
	}

	private boolean compareIdentityLines(List<RpcBodyLine> expected, List<RpcBodyLine> actual) {
		final Function<List<RpcBodyLine>, String> encode = x -> x.stream().map(y -> y.toJSON().toString())
				.collect(Collectors.joining("\n"));

		final String lhs = encode.apply(filterOutStdio(expected));
		final String rhs = encode.apply(filterOutStdio(actual));
		return lhs.equals(rhs);
	}

	private void runEvaluateProperty(EvaluatePropertyReq req, Consumer<SynchronousEvaluationResult> responseHandler) {

//		final Object lock = new Object();
		final Consumer<JSONObject> handleCallback = rawMsg -> {
			final EvaluatePropertyRes res = EvaluatePropertyRes.fromJSON(rawMsg);
//			synchronized (lock) {
				switch (res.response.type) {
				case job:
					// Do nothing
					break;
				case sync:
					responseHandler.accept(res.response.asSync());
					break;
				default:
					System.out.println("Unknown response type");
					break;
				}
//			}
		};

		handleCallback.accept(requestHandler.handleRequest(new ClientRequest(req.toJSON(), asyncMsg -> {
//			System.out.println("AsyncUpdate: " + asyncMsg.toJSON());
//					AsyncRpcUpdate.fromJSON(asyncMsg);
			switch (asyncMsg.value.type) {
//					case status:
//						break;
			case workerTaskDone:
				final WorkerTaskDone tdone = asyncMsg.value.asWorkerTaskDone();
				switch (tdone.type) {
				case normal:
					responseHandler.accept(EvaluatePropertyRes.fromJSON(tdone.asNormal()).response.asSync());
					break;
				case unexpectedError:
				default:
					throw new RuntimeException("Unexpected WorkerTaskDone result");
				}
			default:
				break;
			}
//					handleCallback.accept(asyncMsg.toJSON());
		}, new AtomicBoolean(true))));
	}

	private void doRunNested(boolean allowAsync, boolean identityComparison, String where, ParsingRequestData src,
			List<RpcBodyLine> lines, List<NestedTest> tests, Consumer<TestResult> onDone) {
		final AtomicReference<Consumer<Integer>> runTestPtr = new AtomicReference<>();
		runTestPtr.set(testIdx -> {
			if (testIdx >= tests.size()) {
				onDone.accept(null);
				return;
			}
			final NestedTest nt = tests.get(testIdx);

			final String ourWhere = String.format("%s.%s()", where.length() == 0 ? "" : (where + " > "),
					nt.property.name);
			final NodeLocator locator = findLocatorWithNestingPath(nt.path, 0, lines);
			if (locator == null) {
				onDone.accept(
						new TestResult(false, ourWhere, nt.expectedOutput, Collections.emptyList(), new ArrayList<>()));
				return;
			}

			final EvaluatePropertyReq req = new EvaluatePropertyReq(src, locator, nt.property, false,
					allowAsync ? jobIdGenerator.getAndIncrement() : null, null);
			runEvaluateProperty(req, sync -> {
				if (!(identityComparison //
						? compareIdentityLines(nt.expectedOutput, sync.body)
								: compareSetLines(nt.expectedOutput, sync.body))) {
					onDone.accept(new TestResult(false, ourWhere, nt.expectedOutput, sync.body, new ArrayList<>()));
					return;
				}
				doRunNested(allowAsync, identityComparison, ourWhere, src, sync.body, nt.nestedProperties, tr -> {
					if (tr != null) {
						onDone.accept(tr);
					} else {
						runTestPtr.get().accept(testIdx + 1);
					}
				});
			});
//			final EvaluatePropertyRes res = EvaluatePropertyRes
//					.fromJSON(requestHandler.handleRequest(new ClientRequest(req.toJSON(), (am) -> {
//					}, new AtomicBoolean(true))));
//			final SynchronousEvaluationResult sync;
//			switch (res.response.type) {
//			case job:
//				throw new RuntimeException("Unexpected async response to sync request");
//			case sync:
//				sync = res.response.asSync();
//				break;
//			default:
//				throw new RuntimeException("Unknown response type");
//			}
		});
		runTestPtr.get().accept(0);
	}

	private void doRunTest(TestCase tcase, Consumer<TestResult> callback, boolean allowAsync) {
		// TODO use debugLines on errors
		final List<String> debugLines = new ArrayList<>();
		final StdIoInterceptor rootInterceptor = allowAsync ? null : new StdIoInterceptor(false) {

			@Override
			public void onLine(boolean stdout, String line) {
				debugLines.add(line);
			}
		};
		if (rootInterceptor != null) {
			rootInterceptor.install();
		}
		try {
			final Object lock = new Object();
//			final AtomicReference<JSONObject> responsePtr = new AtomicReference<>();

			final Consumer<SynchronousEvaluationResult> handleResponse = response -> {
				if (rootInterceptor != null) {
					rootInterceptor.flush();
					rootInterceptor.restore();
				}
				CodeProber.flog("handleResp: " + response);

				final List<RpcBodyLine> actual = response.body;

//				final TestCaseAssert assertion = tcase.expected;
				switch (tcase.assertType) {
//				case
				case SMOKE: {
					System.out.println("smoke test - todo check if errors perhaps?");
					callback.accept(
							new TestResult(true, "", Collections.emptyList(), Collections.emptyList(), debugLines));
					break;
				}
				case IDENTITY: {
					if (!compareIdentityLines(tcase.expectedOutput, actual)) {
						callback.accept(new TestResult(false, "", tcase.expectedOutput, actual, debugLines));
						break;
					}
					if (tcase.nestedProperties.isEmpty()) {
						callback.accept(new TestResult(true, "", tcase.expectedOutput, actual, debugLines));
					} else {
						doRunNested(allowAsync, true, "", tcase.src, actual, tcase.nestedProperties, res -> {
							if (res != null) {
								callback.accept(res.replaceStdoutLines(debugLines));
							} else {
								callback.accept(new TestResult(true, "", tcase.expectedOutput, actual, debugLines));
							}
						});
					}
					break;
				}
				case SET: {
					if (!compareSetLines(tcase.expectedOutput, actual)) {
						callback.accept(new TestResult(false, "", tcase.expectedOutput, actual, debugLines));
						break;
					}
					if (tcase.nestedProperties.isEmpty()) {
						callback.accept(new TestResult(true, "", tcase.expectedOutput, actual, debugLines));
					} else {
						doRunNested(allowAsync, false, "", tcase.src, actual, tcase.nestedProperties, res -> {
							if (res != null) {
								callback.accept(res.replaceStdoutLines(debugLines));
							} else {
								callback.accept(new TestResult(true, "", tcase.expectedOutput, actual, debugLines));
							}
						});
					}
//					final TestResult nestedResult = doRunNested(false, "", tcase.src, actual, tcase.nestedProperties);
//					if (nestedResult != null) {
//						callback.accept(nestedResult.replaceStdoutLines(debugLines));
//						break;
//					}
//					callback.accept(new TestResult(true, "", tcase.expectedOutput, actual, debugLines));
					break;
				}

				default: {
					System.err.println("TODO support some other assertion type " + tcase.assertType);
					callback.accept(new TestResult(false, "",
							Arrays.asList(RpcBodyLine.fromPlain("<Malformed Test File>")), actual, debugLines));
					break;
				}
				}
			};

//			final Consumer<JSONObject> handleCallback = rawMsg -> {
//				final EvaluatePropertyRes res = EvaluatePropertyRes.fromJSON(rawMsg);
//				synchronized (lock) {
//					switch (res.response.type) {
//					case job:
//						// Do nothing
//						break;
//					case sync:
//						handleResponse.accept(res.response.asSync());
//						break;
//					default:
//						System.out.println("Unknown response type");
//						break;
//					}
//				}
//			};
			Long job = null;
			if (allowAsync) {
				job = jobIdGenerator.getAndIncrement();
//				req.put("job", jobIdGenerator.getAndIncrement());
//				CodeProber.flog("-- TestClient :: starting job " + req);
			}

			final EvaluatePropertyReq req = new EvaluatePropertyReq(tcase.src, tcase.locator, tcase.property, false,
					job, null);

			runEvaluateProperty(req, handleResponse);
//			handleCallback.accept(requestHandler.handleRequest(new ClientRequest(req.toJSON(), asyncMsg -> {
////						AsyncRpcUpdate.fromJSON(asyncMsg);
//				switch (asyncMsg.value.type) {
////						case status:
////							break;
//				case workerTaskDone:
//					final WorkerTaskDone tdone = asyncMsg.value.asWorkerTaskDone();
//					switch (tdone.type) {
//					case normal:
//						handleResponse.accept(EvaluatePropertyRes.fromJSON(tdone.asNormal()).response.asSync());
//						break;
//					case unexpectedError:
//					default:
//						throw new RuntimeException("Unexpected WorkerTaskDone result");
//					}
//				default:
//					break;
//				}
////						handleCallback.accept(asyncMsg.toJSON());
//			}, new AtomicBoolean(true))));
		} catch (RuntimeException e) {
			if (rootInterceptor != null) {
				rootInterceptor.flush();
				rootInterceptor.restore();
			}
			System.err.println("Error when submitting test");
			e.printStackTrace();
			if (rootInterceptor != null) {
				System.out.println("Debug lines during tests:");
				for (String s : debugLines) {
					System.out.println("> " + s);
				}
			}
		}
	}

	public void runTestAsync(TestCase tcase, Consumer<TestResult> onDone) {
		doRunTest(tcase, onDone, true);
	}

	public TestResult runTest(TestCase tcase) {
		final AtomicReference<TestResult> ptr = new AtomicReference<>();
		doRunTest(tcase, resp -> {
			ptr.set(resp);
			synchronized (ptr) {
				ptr.notifyAll();
			}
		}, false);
		synchronized (ptr) {
			while (ptr.get() == null) {
				try {
					ptr.wait();
				} catch (InterruptedException e) {
					System.err.println("Interrupted while synchronously waiting for test to run");
					throw new RuntimeException(e);
				}
			}
		}
		return ptr.get();
	}

	public static class TestResult {
		public final boolean pass;
		public final String whereInNestedPropertyIfAny;
		public final List<RpcBodyLine> expected;
		public final List<RpcBodyLine> actual;
		public final List<String> stdoutLinesDuringParsing;

		public TestResult(boolean pass, String where, List<RpcBodyLine> expected, List<RpcBodyLine> actual,
				List<String> stdoutLinesDuringParsing) {
			this.pass = pass;
			this.whereInNestedPropertyIfAny = where;
			this.expected = expected;
			this.actual = actual;
			this.stdoutLinesDuringParsing = stdoutLinesDuringParsing;
		}

		public TestResult replaceStdoutLines(List<String> newLines) {
			return new TestResult(pass, whereInNestedPropertyIfAny, expected, actual, newLines);
		}
	}
}
