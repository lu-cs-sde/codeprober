package codeprober.util;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ParsedArgs {
	public static enum ConcurrencyMode {
		DISABLED, COORDINATOR, WORKER,
	}

	public final boolean runTest;
	public final ConcurrencyMode concurrencyMode;
	public final Integer workerProcessCount;
	public final String jarPath;
	public final String[] extraArgs;
	public final String oneshotRequest;
	public final File oneshotOutput;

	public ParsedArgs(boolean runTest, ConcurrencyMode concurrencyMode, Integer workerProcessCount, String jarPath,
			String[] extraArgs, String oneshotRequest, File oneshotOutput) {
		this.runTest = runTest;
		this.concurrencyMode = concurrencyMode;
		this.workerProcessCount = workerProcessCount;
		this.jarPath = jarPath;
		this.extraArgs = extraArgs;
		this.oneshotRequest = oneshotRequest;
		this.oneshotOutput = oneshotOutput;
	}

	public static void printUsage() {
		System.out.println(
				"Usage: java -jar codeprober.jar [--test] [--concurrent=N] [path/to/your/analyzer-or-compiler.jar [args-to-forward-to-your-main]]");
	}

	public static ParsedArgs parse(String[] args) {
		boolean runTests = false;
		AtomicReference<ConcurrencyMode> concurrency = new AtomicReference<>(ConcurrencyMode.DISABLED);
		final Consumer<ConcurrencyMode> setConcurrencyMode = (mode -> {
			if (concurrency.get() != ConcurrencyMode.DISABLED) {
				throw new IllegalArgumentException(
						"Can only specify either '--concurrent', '--concurrent=[processCount]' or '--worker', not multiple options simultaneously");
			}
			concurrency.set(mode);
		});

		String jarPath = null;
		String[] extraArgs = null;
		Integer workerCount = null;
		String oneshotRequest = null;
		File oneshotOutput = null;

		gatherArgs: for (int i = 0; i < args.length; ++i) {
			switch (args[i]) {
			case "--test": {
				if (runTests) {
					throw new IllegalArgumentException("Duplicate '--test'");
				}
				runTests = true;
				break;
			}
			case "--concurrent": {
				setConcurrencyMode.accept(ConcurrencyMode.COORDINATOR);
				break;
			}
			case "--worker": {

				setConcurrencyMode.accept(ConcurrencyMode.WORKER);
				break;
			}
			case "--help": {
				printUsage();
				System.exit(1);
				break;
			}

			default: {
				if (args[i].startsWith("--oneshot=")) {
					if (oneshotRequest != null) {
						throw new IllegalArgumentException("Cannot specify multiple --oneshot values");
					}
					oneshotRequest = args[i].substring("--oneshot=".length());
					continue;
				}
				if (args[i].startsWith("--output=")) {
					if (oneshotOutput != null) {
						throw new IllegalArgumentException("Cannot specify multiple --output values");
					}
					oneshotOutput = new File(args[i].substring("--output=".length())).getAbsoluteFile();
					if (oneshotOutput.getParentFile() == null || !oneshotOutput.getParentFile().exists()) {
						throw new IllegalArgumentException(
								"Directory of output file ('" + oneshotOutput + "') does not exist");
					}
					continue;
				}
				if (args[i].startsWith("--concurrent=")) {
					setConcurrencyMode.accept(ConcurrencyMode.COORDINATOR);
					try {
						workerCount = Integer.parseInt(args[i].substring("--concurrent=".length()));
						if (workerCount <= 0) {
							throw new IllegalArgumentException("Minimum worker count is 1, got '" + workerCount + "'");
						}
					} catch (NumberFormatException e) {
						System.out.println("Invalid value for '--concurrent'");
						e.printStackTrace();
						System.exit(1);
					}
				} else {
					jarPath = args[i];
					extraArgs = Arrays.copyOfRange(args, i + 1, args.length);
					break gatherArgs;
				}
			}
			}
		}
		if (oneshotRequest != null) {
			if (jarPath == null) {
				throw new IllegalArgumentException("Must specify analyzer-or-compiler.jar when using --oneshot");
			}
			if (runTests) {
				throw new IllegalArgumentException("Cannot run tests and handle oneshot requests at the same time");
			}
			if (concurrency.get() != ConcurrencyMode.DISABLED) {
				throw new IllegalArgumentException("Concurrency is not supported for oneshot requests");
			}
			if (oneshotOutput == null) {
				throw new IllegalArgumentException("Must specify --output= as well when running oneshot requests");
			}
		} else {
			if (oneshotOutput != null) {
				throw new IllegalArgumentException("--output= is only valid together with --oneshot=");
			}
		}
		return new ParsedArgs(runTests, concurrency.get(), workerCount, jarPath, extraArgs, oneshotRequest,
				oneshotOutput);
	}
}
