package codeprober.util;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import codeprober.CodeProber;

public class ParsedArgs {
	public static enum ConcurrencyMode {
		DISABLED, COORDINATOR, WORKER,
	}

	public final boolean runTest;
	public final ConcurrencyMode concurrencyMode;
	public final Integer workerProcessCount;
	public final String jarPath;
	public final String[] extraArgs;

	public ParsedArgs(boolean runTest, ConcurrencyMode concurrencyMode, Integer workerProcessCount, String jarPath,
			String[] extraArgs) {
		this.runTest = runTest;
		this.concurrencyMode = concurrencyMode;
		this.workerProcessCount = workerProcessCount;
		this.jarPath = jarPath;
		this.extraArgs = extraArgs;
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

			default: {
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
		if (jarPath == null) {
			CodeProber.printUsage();
			System.exit(0);
		}
		return new ParsedArgs(runTests, concurrency.get(), workerCount, jarPath, extraArgs);
	}
}
