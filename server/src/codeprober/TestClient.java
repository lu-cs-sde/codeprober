package codeprober;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.ProbeProtocol;
import codeprober.protocol.TestProtocol;
import codeprober.rpc.JsonRequestHandler;

public class TestClient {

	private final JsonRequestHandler requestHandler;
	private final AtomicLong jobIdGenerator = new AtomicLong();

	public TestClient(JsonRequestHandler requestHandler) {
		this.requestHandler = requestHandler;
	}

	private ClientRequest constructMessage(JSONObject query) {
		return new ClientRequest(new JSONObject() //
				.put("type", TestProtocol.type) //
				.put("query", query), obj -> {
				}, new AtomicBoolean(true));
	}

	public JSONArray getTestSuites() {
		return TestProtocol.ListTestSuites.suites.get(requestHandler.handleRequest( //
				constructMessage(new JSONObject() //
						.put("type", TestProtocol.ListTestSuites.type))));
	}

	public JSONArray getTestSuiteContents(String suiteName) {
		final JSONObject request = new JSONObject() //
				.put("type", TestProtocol.GetTestSuite.type);
		TestProtocol.GetTestSuite.suite.put(request, suiteName);
		return new JSONArray(TestProtocol.GetTestSuite.contents.get(requestHandler.handleRequest( //
				constructMessage(request))));
	}

	private void doRunTest(JSONObject tcase, Consumer<TestResult> callback, boolean allowAsync) {
		final JSONObject req = new JSONObject().put("type", "query");
		ProbeProtocol.cache.put(req, ProbeProtocol.cache.get(tcase));
		ProbeProtocol.positionRecovery.put(req, ProbeProtocol.positionRecovery.get(tcase));
		ProbeProtocol.text.put(req, tcase.getString("src"));
		ProbeProtocol.captureStdout.put(req, false);
		final JSONArray args = ProbeProtocol.mainArgs.get(tcase, null);
		if (args != null) {
			ProbeProtocol.mainArgs.put(req, args);
		}
		ProbeProtocol.tmpSuffix.put(req, ProbeProtocol.tmpSuffix.get(tcase));

		final JSONObject query = new JSONObject();
		ProbeProtocol.Query.attribute.put(query, tcase.getJSONObject("attribute"));
		ProbeProtocol.Query.locator.put(query, tcase.getJSONObject("locator").getJSONObject("robust"));
		ProbeProtocol.query.put(req, query);

//		final JSONObject response;

		// TODO use debugLines on errors
		final List<String> debugLines = new ArrayList<>();
		final StdIoInterceptor rootInterceptor = new StdIoInterceptor(false) {

			@Override
			public void onLine(boolean stdout, String line) {
				debugLines.add(line);
			}
		};
		rootInterceptor.install();
		try {
			final Object lock = new Object();
//			final AtomicReference<JSONObject> responsePtr = new AtomicReference<>();
			final Consumer<JSONObject> handleResponse = response -> {
				rootInterceptor.flush();
				rootInterceptor.restore();

				final JSONArray actual = ProbeProtocol.body.get(response);

				final JSONObject assertion = tcase.getJSONObject("assert");
				switch (assertion.getString("type")) {
				case "smoke": {
					System.out.println("smoke test - todo check if errors perhaps?");
					callback.accept(new TestResult(true, new JSONArray(), new JSONArray(), debugLines));
					break;
				}
				case "identity": {
					final JSONArray expected = assertion.getJSONArray("lines");
					callback.accept(new TestResult(expected.toString().equals(actual.toString()), expected, actual,
							debugLines));
					break;
				}
				default: {
					System.err.println("TODO support some other assertion type " + assertion);
					callback.accept(
							new TestResult(false, new JSONArray().put("<Malformed Test File>"), actual, debugLines));
					break;
				}
				}
			};

			final Consumer<JSONObject> handleCallback = msg -> {
//				System.out.println("handleCallback: " + msg);
				synchronized (lock) {
					if (allowAsync) {
						if (msg.has("type") && msg.get("type").equals("jobUpdate")) {
							final JSONObject res = msg.getJSONObject("result");
							if (res.get("status").equals("done")) {
								handleResponse.accept(res.getJSONObject("result"));
//								responsePtr.set(res.getJSONObject("result"));
//								lock.notifyAll();
								return;
							}
						}
					} else {
						handleResponse.accept(msg);
//						responsePtr.set(msg);
//						lock.notifyAll();
					}
				}
			};
			if (allowAsync) {
				req.put("job", jobIdGenerator.getAndIncrement());
				CodeProber.flog("-- TestClient :: starting job " + req);
			}

			System.out.println("send it");
			handleCallback.accept(requestHandler
					.handleRequest(new ClientRequest(req, handleCallback::accept, new AtomicBoolean(true))));
		} catch (RuntimeException e) {
			rootInterceptor.flush();
			rootInterceptor.restore();
			System.err.println("Error when submitting test");
			e.printStackTrace();
			System.out.println("Debug lines during tests:");
			for (String s : debugLines) {
				System.out.println("> " + s);
			}

//			synchronized (lock) {
//				while (responsePtr.get() == null) {
//					try {
//						lock.wait();
//					} catch (InterruptedException e) {
//						System.out.println("Test interrupted");
//						e.printStackTrace();
//					}
//				}
//				response = responsePtr.get();
//			}
		}
	}

	public void runTestAsync(JSONObject tcase, Consumer<TestResult> onDone) {
		doRunTest(tcase, onDone, true);
	}

	public TestResult runTest(JSONObject tcase) {
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
		public final JSONArray expected;
		public final JSONArray actual;
		public final List<String> stdoutLinesDuringParsing;

		public TestResult(boolean pass, JSONArray expected, JSONArray actual, List<String> stdoutLinesDuringParsing) {
			this.pass = pass;
			this.expected = expected;
			this.actual = actual;
			this.stdoutLinesDuringParsing = stdoutLinesDuringParsing;
		}
	}
}
