package codeprober;

import java.util.ArrayList;
import java.util.List;

import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class JUnitTestRunnerWrapper {

	public static void main(String[] args) throws ClassNotFoundException {
		JUnitCore junit = new JUnitCore();

		junit.addListener(new RunListener() {
			@Override
			public void testStarted(Description description) {
				System.out.println("Starting: " + description.getMethodName());
			}

			@Override
			public void testFinished(Description description) {
				System.out.println("Finished: " + description.getMethodName());
			}

			@Override
			public void testFailure(Failure failure) {
				System.out.println("Failed: " + failure.getDescription().getMethodName());
				failure.getException().printStackTrace();
			}
		});

		List<Class<?>> classes = new ArrayList<>();
		for (String s : args) {
			classes.add(Class.forName(s));
		}

		Result result = junit.run(classes.toArray(new Class[classes.size()]));
		System.out.println("\nTests run: " + result.getRunCount());
		final int numFailures = result.getFailureCount();
		System.out.println("Failures: " + numFailures);
		System.exit(numFailures);
	}
}
