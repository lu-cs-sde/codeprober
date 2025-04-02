package codeprober;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import codeprober.TestClient.TestResult;
import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.protocol.data.TestCase;
import codeprober.protocol.data.TestSuite;

public class RunAllTests {

	public static enum MergedResult {
		ALL_PASS, SOME_FAIL,
	}

	public static MergedResult run(TestClient client, boolean runConcurrently) {
		final List<String> suites = client.getTestSuites();
		if (suites == null) {
			System.err.println("Error when listing test suites - is '-Dcpr.testDir' set?");
			System.exit(1);
		}

		final AtomicInteger errCounter = new AtomicInteger();
		final AtomicInteger successCounter = new AtomicInteger();
//		final ExecutorService executor = runConcurrently ? Executors.newFixedThreadPool(4) : null;
		for (int i = 0; i < suites.size(); ++i) {
			final String suiteName = suites.get(i);
//			final JSONObject request = new JSONObject() //
//					.put("type", TestProtocol.GetTestSuite.type);
//			TestProtocol.GetTestSuite.suite.put(request, suite);

			CodeProber.flog("Start suite " + suiteName + ", to " + System.out + " on thread " + Thread.currentThread()
					+ " ;; intercept count: " + StdIoInterceptor.installCount.get());
			System.out.println(": " + suiteName);
			// BEGIN: Thing that will break when JSON format is updated
			final TestSuite suite = client.getTestSuiteContents(suiteName);

			final CountDownLatch cdl = runConcurrently ? new CountDownLatch(suite.cases.size()) : null;
			final BiConsumer<String, TestResult> handleResult = (name, result) -> {
				if (result.pass) {
					System.out.println("  ✅ " + name);
					successCounter.incrementAndGet();
				} else {
					System.out.println("  ❌ " + name);
					errCounter.incrementAndGet();
				}
				if (cdl != null) {
					cdl.countDown();
				}
			};
			for (int j = 0; j < suite.cases.size(); ++j) {
				final TestCase tcase = suite.cases.get(j);

//				final String name = tcase.getString("name");
				if (runConcurrently) {
					try {
						CodeProber.flog("\n\nstart rAT " + tcase.name);
						client.runTestAsync(tcase, tr -> {
							try {
								handleResult.accept(tcase.name, tr);
							} catch (Throwable t) {
								System.out.println("Error while handling callback");
								t.printStackTrace();
							}
						});
					} catch (Throwable t) {
						System.out.println("Exception thrown while running test " + tcase.name);
						t.printStackTrace();
						System.exit(1);
//							anyErr.set(true);
//							executor.shutdown();
//							cdl.countDown();
//							cdl.notifyAll();
					}
				} else {
					handleResult.accept(tcase.name, client.runTest(tcase));
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

//		if (executor != null) {
//			executor.shutdown();
//		}
		final int numErr = errCounter.get();
		final int numSuccess = successCounter.get();
		if (numErr == 0) {
			switch (numSuccess) {
			case 0: {
				System.out.println("No tests found..");
				break;
			}
			case 1: {
				System.out.println("Pass 1 test");
				break;
			}
			default: {
				System.out.println("Pass " + numSuccess + " tests");
				break;
			}
			}
			return MergedResult.ALL_PASS;
		}

		if (numSuccess == 0) {
			if (numErr == 0) {
				System.out.println("Fail 1 test");
			} else {
				System.out.println("Fail all " + numErr + " tests");
			}
		} else {
			System.out.println("Pass " + numSuccess + "/" + (numErr + numSuccess) + " tests");
		}

		return MergedResult.SOME_FAIL;
	}
}
