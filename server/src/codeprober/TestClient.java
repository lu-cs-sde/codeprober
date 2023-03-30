package codeprober;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.protocol.ProbeProtocol;
import codeprober.protocol.TestProtocol;
import codeprober.rpc.JsonRequestHandler;

public class TestClient {

	private final JsonRequestHandler requestHandler;

	public TestClient(JsonRequestHandler requestHandler) {
		this.requestHandler = requestHandler;
	}

	private JSONObject constructMessage(JSONObject query) {
		return new JSONObject() //
				.put("type", TestProtocol.type) //
				.put("query", query);
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

	public TestResult runTest(JSONObject tcase) {
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

		final JSONObject response;

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
			response = requestHandler.handleRequest(req);
			System.out.println("respnse: " + response);
		} finally {
			rootInterceptor.flush();
			rootInterceptor.restore();
		}

		final JSONArray actual = ProbeProtocol.body.get(response);

		final JSONObject assertion = tcase.getJSONObject("assert");
		switch (assertion.getString("type")) {
		case "smoke": {
			System.out.println("smoke test - todo check if errors perhaps?");
			return new TestResult(true, new JSONArray(), new JSONArray(), debugLines);
		}
		case "identity": {
			final JSONArray expected = assertion.getJSONArray("lines");
			return new TestResult(expected.toString().equals(actual.toString()), expected, actual, debugLines);
		}
		default: {
			System.err.println("TODO support some other assertion type " + assertion);
			return new TestResult(false, new JSONArray().put("<Malformed Test File>"), actual, debugLines);
		}
		}
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
