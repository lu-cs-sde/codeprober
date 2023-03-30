package codeprober;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.protocol.TestProtocol;

public class RunAllTests {

	public static void run(TestClient client) {
		final JSONArray suites = client.getTestSuites();
		if (suites == null) {
			System.err.println("Error when listing test suites - is '-Dcpr.testDir' set?");
			System.exit(1);
		}

		for (int i = 0; i < suites.length(); ++i) {
			final String suite = suites.getString(i);
			final JSONObject request = new JSONObject() //
					.put("type", TestProtocol.GetTestSuite.type);
			TestProtocol.GetTestSuite.suite.put(request, suite);

			System.out.println(": " + suite);
			// BEGIN: Thing that will break when JSON format is updated
			final JSONArray cases = client.getTestSuiteContents(suite);
			for (int j = 0; j < cases.length(); ++j) {
				final JSONObject tcase = cases.getJSONObject(j);
				final String name = tcase.getString("name");
				if (client.runTest(tcase).pass) {
					System.out.println("  ✅ " + name);
				} else {
					System.out.println("  ❌ " + name);
				}
			}
			// END: Thing that will break when JSON format is updated
		}
	}
}
