package codeprober;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.TestClient.TestResult;
import codeprober.protocol.TestProtocol;

public class RunAllTests {

	public static enum MergedResult {
		ALL_PASS, SOME_FAIL,
	}

	public static MergedResult run(TestClient client, boolean runConcurrently) {
		final JSONArray suites = client.getTestSuites();
		if (suites == null) {
			System.err.println("Error when listing test suites - is '-Dcpr.testDir' set?");
			System.exit(1);
		}

		final AtomicBoolean anyErr = new AtomicBoolean();
		final ExecutorService executor = runConcurrently ? Executors.newFixedThreadPool(4) : null;
		for (int i = 0; i < suites.length(); ++i) {
			final String suite = suites.getString(i);
			final JSONObject request = new JSONObject() //
					.put("type", TestProtocol.GetTestSuite.type);
			TestProtocol.GetTestSuite.suite.put(request, suite);

			System.out.println(": " + suite);
			// BEGIN: Thing that will break when JSON format is updated
			final JSONArray cases = client.getTestSuiteContents(suite);

			final CountDownLatch cdl = executor != null ? new CountDownLatch(cases.length()) : null;

			final BiConsumer<String, TestResult> handleResult = (name, result) -> {
				if (result.pass) {
					System.out.println("  ✅ " + name);
				} else {
					System.out.println("  ❌ " + name);
					anyErr.set(true);
				}
				if (cdl != null) {
					cdl.countDown();
				}
			};
			for (int j = 0; j < cases.length(); ++j) {
				final JSONObject tcase = cases.getJSONObject(j);

				final String name = tcase.getString("name");
				if (executor != null) {
					try {
						CodeProber.flog("\n\nstart rAT " + name);
						client.runTestAsync(tcase, tr -> {
							try {
								handleResult.accept(name, tr);
							} catch (Throwable t) {
								System.out.println("Error while handling callback");
								t.printStackTrace();
							}
						});
					} catch (Throwable t) {
						System.out.println("Exception thrown while running test " + name);
						t.printStackTrace();
						System.exit(1);
//							anyErr.set(true);
//							executor.shutdown();
//							cdl.countDown();
//							cdl.notifyAll();
					}
					executor.submit(() -> {
					});
				} else {
					handleResult.accept(name, client.runTest(tcase));
				}
			}
			if (cdl != null) {
				try {
					cdl.await();
				} catch (InterruptedException e) {
					System.err.println("Interrupted while waiting for tests to finish");
					e.printStackTrace();
				}
			}
			// END: Thing that will break when JSON format is updated
		}

		if (executor != null) {
			executor.shutdown();
		}
		return anyErr.get() ? MergedResult.SOME_FAIL : MergedResult.ALL_PASS;
	}
}
