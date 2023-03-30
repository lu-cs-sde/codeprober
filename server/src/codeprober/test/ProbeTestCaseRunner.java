package codeprober.test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

public class ProbeTestCaseRunner extends Runner implements Filterable {

	private Class<?> testClazz;

	private List<TestImpl> tests = null;

	public ProbeTestCaseRunner(Class<?> testClazz) {
		this.testClazz = testClazz;
	}

	@SuppressWarnings("unchecked")
	private void init() {
		if (tests != null) {
			return;
		}

		try {
			List<ProbeTestCase> ptcs = (List<ProbeTestCase>) testClazz.getMethod("getTestCases").invoke(null);
			tests = new ArrayList<>();
			for (ProbeTestCase ptc : ptcs) {
				tests.add(new TestImpl(ptc, Description.createTestDescription(testClazz, ptc.name())));
			}
		} catch (ClassCastException e) {
			throw new RuntimeException("Wrong result type from getTestCases, expected 'java.util.List<"
					+ ProbeTestCase.class.getName() + ">'", e);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(
					"Missing the following static function in the test class: static List<ProbeTestCase> getTestCases() { ... }",
					e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Description getDescription() {
		init();
		final Description sdesc = Description.createTestDescription(testClazz, "getTestCases" );
		for (TestImpl ti : tests) {
			sdesc.addChild(ti.desc);
		}
		return sdesc;
	}

	@Override
	public void run(RunNotifier notifier) {
		init();
		try {
			final Description suiteDesc = getDescription();
			notifier.fireTestSuiteStarted(suiteDesc);
			for (TestImpl ti : tests) {
				notifier.fireTestStarted(ti.desc);
				try {
					ti.ptc.assertPass();
				} catch (Error e) {
					notifier.fireTestFailure(new Failure(ti.desc, e));
				}
				notifier.fireTestFinished(ti.desc);
			}
			notifier.fireTestSuiteFinished(suiteDesc);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void filter(Filter f) throws NoTestsRemainException {
		init();
		final Iterator<TestImpl> iter = tests.iterator();
		while (iter.hasNext()) {
			if (!f.shouldRun(iter.next().desc)) {
				iter.remove();
			}
		}
	}

	private static class TestImpl {
		public final ProbeTestCase ptc;
		public final Description desc;

		public TestImpl(ProbeTestCase ptc, Description desc) {
			this.ptc = ptc;
			this.desc = desc;
		}
	}
}