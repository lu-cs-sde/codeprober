package codeprober.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ParsedArgs {
	public static enum ConcurrencyMode {
		DISABLED, COORDINATOR, WORKER,
	}

	public static enum TestMode {
		DISABLED, RUN, BLESS_DRY, BLESS_WET,;

		public boolean isBlessLike() {
			return this == BLESS_DRY || this == BLESS_WET;
		}
	}

	public final TestMode testMode;
	public final ConcurrencyMode concurrencyMode;
	public final Integer workerProcessCount;
	public final String jarPath;
	public final String[] extraArgs;
	public final String oneshotRequest;
	public final File oneshotOutput;

	public ParsedArgs(TestMode testMode, ConcurrencyMode concurrencyMode, Integer workerProcessCount, String jarPath,
			String[] extraArgs, String oneshotRequest, File oneshotOutput) {
		this.testMode = testMode;
		this.concurrencyMode = concurrencyMode;
		this.workerProcessCount = workerProcessCount;
		this.jarPath = jarPath;
		this.extraArgs = extraArgs;
		this.oneshotRequest = oneshotRequest;
		this.oneshotOutput = oneshotOutput;
	}

	public static void printUsage() {
		System.out.println(
				"Usage: java -jar codeprober.jar [--test[=run|bless|bless-dry] [--concurrent=N] [path/to/your/analyzer-or-compiler.jar [args-to-forward-to-your-main]]");
		System.out.println("");
		System.out.println("Option descriptions:");

		BiConsumer<String, String> addOption = (title, message) -> {
			System.out.println();
			System.out.printf("  %s%n", title);
			List<String> lines = wrapText(message, 60);
			for (int i = 0; i < lines.size(); ++i) {
				System.out.printf("%12s %s%n", "", lines.get(i));
			}
		};

		addOption.accept("--test", "Same as --test=run");
		addOption.accept("--test=run",
				"Run all text probes in the workspace. Sets the exit code to 0 if all tests pass, or 1 on any failure.");
		addOption.accept("--test=bless", "Update the expected values of text probes in the workspace. "
				+ "This exists to simplify maintaining a large test suite after intentional changes are done to an underlying tool. "
				+ "To reduce risk of unintended updates, the update mechanism is quite conservative, and any of the following criteria will prevent ALL updates for a given file: \n "
				+ "1) Static semantic text probe errors exist in the file, like a reference to a non-existing text probe variable. \n "
				+ "2) Runtime errors occur during the evaluation of left-hand side of an assertion, or in the right-hand side of a used variable. \n "
				+ " \n " //
				+ "Similarly, individual assertions may be ignored if any following criteria is fulfilled: \n "
				+ "1) The right-hand side of the assertion is a non-primitive. For example, in [[A.b=\"c\"]], [[A.b=123]], [[A.b=$c]] and [[A.b=C]], "
				+ "only the first two probes are updated. \n "
				+ "2) The comparison operator is '!=', '~=' or '!~='. In other words, only '=' is updated");
		addOption.accept("--test=bless-dry",
				"A dry-run version of --test=bless. Will print all updates that would be done.");
		addOption.accept("--concurrent=N",
				"Spawn N separate JVM instances, delegate most tasks to them. This can increase performance. It also makes it possible to interrupt long-running tasks. Normally, probes are evaluated in the same process as CodeProber itself, so an infinite or slow-running task is impossible for CodeProber to cancel. With --concurrent, CodeProber can stop worker processes mid-task. If you are developing analysis tasks that have a tendency to get stuck, this may be a very useful option to enable.");

		System.out.println("");
		System.out.println("Important System Properties:");
		addOption.accept("-Dcpr.workspace=DIR",
				"Use a directory as the 'workspace' within CodeProber. Useful for interacting with persistent data in CodeProber, rather than a temporary file. This system property is mandatory for '--test'.");
		addOption.accept("-Dcpr.workspaceFilePattern=REGEX",
				"Set a regex to select which files in the workspace that are visible to CodeProber. Anything not matching this regex is ignored. For '--test=bless', this can be used to select which subset of the test suite you wish to update.");
		System.out.println();
		System.out.println("For a full list of system properties, see https://codeprober.org/docs/");

	}

	public static List<String> wrapText(String text, int maxWidth) {
		final List<String> lines = new ArrayList<>();
		final String[] words = text.split(" ");
		final StringBuilder currentLine = new StringBuilder();

		for (String word : words) {
			if (word.equals("\n")) {
				lines.add(currentLine.toString());
				currentLine.delete(0, currentLine.length());
			} else if (currentLine.length() == 0) {
				currentLine.append(word);
			} else if (currentLine.length() + 1 + word.length() <= maxWidth) {
				currentLine.append(" ").append(word);
			} else {
				lines.add(currentLine.toString());
				currentLine.delete(0, currentLine.length());
				currentLine.append(word);
			}
		}
		if (currentLine.length() > 0) {
			lines.add(currentLine.toString());
		}

		return lines;
	}

	public static ParsedArgs parse(String[] args) {
		TestMode[] testMode = new TestMode[] { TestMode.DISABLED };
		final Consumer<TestMode> setTestMode = (mode -> {
			if (testMode[0] != TestMode.DISABLED) {
				throw new IllegalArgumentException("Can only specify --test once");
			}
			testMode[0] = mode;
		});
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
				setTestMode.accept(TestMode.RUN);
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
					continue;
				}
				if (args[i].startsWith("--test=")) {
					final String modeStr = args[i].substring("--test=".length());
					switch (modeStr) {
					case "run":
						setTestMode.accept(TestMode.RUN);
						break;
					case "bless":
						setTestMode.accept(TestMode.BLESS_WET);
						break;
					case "bless-dry":
						setTestMode.accept(TestMode.BLESS_DRY);
						break;
					default:
						throw new IllegalArgumentException(
								"Unexpected test mode '" + modeStr + "', expected 'run', 'bless' or 'bless-dry'");
					}
					continue;
				}
				jarPath = args[i];
				extraArgs = Arrays.copyOfRange(args, i + 1, args.length);
				break gatherArgs;
			}
			}
		}
		if (oneshotRequest != null) {
			if (jarPath == null) {
				throw new IllegalArgumentException("Must specify analyzer-or-compiler.jar when using --oneshot");
			}
			if (testMode[0] != TestMode.DISABLED) {
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

		if (testMode[0].isBlessLike() && concurrency.get() != ConcurrencyMode.DISABLED) {
			throw new IllegalArgumentException("Concurrent --bless not supported");
		}
		return new ParsedArgs(testMode[0], concurrency.get(), workerCount, jarPath, extraArgs, oneshotRequest,
				oneshotOutput);
	}
}
