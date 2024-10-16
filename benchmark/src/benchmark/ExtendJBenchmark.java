package benchmark;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

public class ExtendJBenchmark extends BaseBenchmark {

	private static enum ActionType {
		CREATE_PROBE, EVALUATE_PROBE, FULL_PARSE_HOT, FULL_PARSE_COLD;

		public boolean requiresWarmup() {
			return this != FULL_PARSE_COLD;
		}
	};

	private static final int NUM_ITERATIONS;
	private static final int NUM_PROBES;
	private static final ActionType ACTION_TYPE;

	private static final double COV_WARMUP_THRESHOLD;
	private static final int COV_WARMUP_IMPLIED_STEADY_STATE_TIMEOUT;
	private static final int WARMUP_CYCLE_ITERATION_COUNT;

	private static final boolean USE_PRECISE_LOCATORS_IN_EVAL_TESTS;

	static {
		NUM_ITERATIONS = Integer.parseInt(System.getProperty("benchmark.iterations", "1000"));
		NUM_PROBES = Integer.parseInt(System.getProperty("benchmark.probes", "1"));
		ACTION_TYPE = ActionType
				.valueOf(System.getProperty("benchmark.actionType", "evaluate_probe").toUpperCase(Locale.ENGLISH));
		COV_WARMUP_THRESHOLD = Double.parseDouble(System.getProperty("benchmark.warmupCovThreshold", "0.01"));
		COV_WARMUP_IMPLIED_STEADY_STATE_TIMEOUT = Integer
				.parseInt(System.getProperty("benchmark.warmupImpliedSteadyStateTimeout", "10"));
		WARMUP_CYCLE_ITERATION_COUNT = Integer
				.parseInt(System.getProperty("benchmark.warmupCycleIterationCount", "100"));
		USE_PRECISE_LOCATORS_IN_EVAL_TESTS = Boolean
				.parseBoolean(System.getProperty("benchmark.usePreciseLocatorsInEvalTests", "true"));
	}

	private static class Measurement {
		public int numSamples;
		public long endToEndTime;
		public long totalTime;
		public long parseTime;
		public long createLocatorTime;
		public long applyLocatorTime;
		public long attrEvalTime;
		public long nodesAtPositionTime;
		public long pastaAttrsTime;

		public void reset() {
			numSamples = 0;
			endToEndTime = 0;
			parseTime = 0;
			totalTime = 0;
			createLocatorTime = 0;
			applyLocatorTime = 0;
			attrEvalTime = 0;
			nodesAtPositionTime = 0;
			pastaAttrsTime = 0;
		}

		public double getAverageEndToEnd() {
			return endToEndTime / (double) numSamples;
		}

		public double getAverageParseTime() {
			return parseTime / (double) numSamples;
		}

		public double getAverageTotalTime() {
			return totalTime / (double) numSamples;
		}

		public double getAverageCreateLocatorTime() {
			return createLocatorTime / (double) numSamples;
		}

		public double getAverageApplyLocatorTime() {
			return applyLocatorTime / (double) numSamples;
		}

		public double getAverageAttrEvalTime() {
			return attrEvalTime / (double) numSamples;
		}

		public double getAverageNodesAtPositionTime() {
			return nodesAtPositionTime / (double) numSamples;
		}

		public double getAveragePastaAttrsTime() {
			return pastaAttrsTime / (double) numSamples;
		}
	}

	private class EvalTestCase {

		private final BiFunction<JSONArray, String, JSONObject> createQuery;
		private JSONArray impreciseLocator;
		private JSONArray preciseLocator;

		public EvalTestCase(BiFunction<JSONArray, String, JSONObject> createQuery, JSONArray impreciseLocator) {
			this.createQuery = createQuery;
			this.impreciseLocator = impreciseLocator;
			this.preciseLocator = null;
		}

		public void prepare() {
			if (preciseLocator != null || !USE_PRECISE_LOCATORS_IN_EVAL_TESTS) {
				return;
			}
			synchronized (this) {
				expectedId = (int) (Math.random() * Integer.MAX_VALUE);
				addIncomingMessageToMeasurements = false;
			}
			try {
				sendMessageAndWaitForResponse(createQuery.apply(impreciseLocator, generateSourceFile()));
			} catch (InterruptedException e) {
				e.printStackTrace();
				throw new RuntimeException();
			}
			synchronized (this) {
				addIncomingMessageToMeasurements = true;
			}
			preciseLocator = lastResult.getJSONObject("result").getJSONObject("locator").getJSONArray("steps");
		}

		public JSONObject getQuery(String sourceFile) {
			if (!USE_PRECISE_LOCATORS_IN_EVAL_TESTS) {
				return createQuery.apply(impreciseLocator, sourceFile);
			}
			if (preciseLocator == null) {
				throw new IllegalStateException("Must prepare() first");
			}
			return createQuery.apply(preciseLocator, sourceFile);
		}
	}

	private int expectedId = -1;
	private boolean addIncomingMessageToMeasurements = true;
	private long requestStartNanos;
	private Measurement measurements = new Measurement();
	private JSONObject lastResult = new JSONObject();
	private EvalTestCase[] evalCases;

	public ExtendJBenchmark(Consumer<String> postMessage) {
		super(postMessage);

		evalCases = new EvalTestCase[] { //

				// Lookup Object.hashCode
				new EvalTestCase(
						(locator, sourceFile) -> ExtendJQueries.createStdJavaLibQuery(expectedId, sourceFile, locator),
						new JSONArray().put(ExtendJQueries.createLookupTypeStep("java.lang", "Object"))), //

				// compilationUnitList.get(..25%..).isEnumDecl()
				new EvalTestCase(
						(locator, sourceFile) -> ExtendJQueries.createIsEnumDecl(expectedId, sourceFile, locator),
						new JSONArray().put(ExtendJQueries.createLookupTypeDeclForBenchmarkStep(25))), //

				// "theMonacoFile".tal(StringLiteral).getNumChild()
				new EvalTestCase(
						(locator, sourceFile) -> ExtendJQueries.createGetNumChild(expectedId, sourceFile, locator),
						new JSONArray() //
								// Our file ("theMonacoFile") is always last (=100)
								.put(ExtendJQueries.createLookupTypeDeclForBenchmarkStep(100)) //
								.put(ExtendJQueries.createTALStep("org.extendj.ast.StringLiteral", (4 << 12),
										(5 << 12)))), //

				// compilationUnitList.get(..75%..).tal(Modifiers).getNumChild()
				new EvalTestCase(
						(locator, sourceFile) -> ExtendJQueries.createGetNumChild(expectedId, sourceFile, locator),
						new JSONArray() //
								.put(ExtendJQueries.createLookupTypeDeclForBenchmarkStep(75)) //
								.put(ExtendJQueries.createTALStep("org.extendj.ast.Modifiers", (1 << 12), (1024 << 12)))

				), //

		};
		// TODO Auto-generated constructor stub
	}

//	private Set<String> uniqResponses = new HashSet<>();
	@Override
	public void handleIncomingMessage(JSONObject msg) {
		if (!msg.has("id")) {
			System.err.println("Got unknown message: " + msg);
			return;
		}
		synchronized (this) {
			final int actualid = msg.getInt("id");
//			System.out.println("Got msg with id " + actualid +", was waiting for " + expectedId);
			if (actualid != expectedId) {
				System.out.println("Unknown RPC response: " + msg.toString());
				System.out.println("Expected id: " + expectedId);
				return;
			}
			if (!msg.has("result")) {
				System.err.println("Got response without result: " + msg);
				return;
			}
			final JSONObject res = msg.getJSONObject("result");
			if (!res.has("locator")) {
				System.out.println("Got response without locator - query or test config is probably wrong: " + msg);
				System.out.println("Last query: " + lastSentMessage);
				return;
			}
//			System.out.println(msg);
			lastResult = msg;
//			final String msgStr = "" + msg.getJSONObject("result").get("body");
//			if (!uniqResponses.contains(msgStr)) {
//				uniqResponses.add(msgStr);
//				System.out.println("UNIQ resp: " + msgStr +"  | from " + lastSentMessage);
//			}

			if (addIncomingMessageToMeasurements) {
				measurements.numSamples++;
				measurements.endToEndTime += System.nanoTime() - requestStartNanos;

				measurements.totalTime += res.getLong("totalTime");
				measurements.parseTime += res.getLong("parseTime");
				measurements.createLocatorTime += res.getLong("createLocatorTime");
				measurements.applyLocatorTime += res.getLong("applyLocatorTime");
				measurements.attrEvalTime += res.getLong("attrEvalTime");
				measurements.nodesAtPositionTime += res.getLong("nodesAtPositionTime");
				measurements.pastaAttrsTime += res.getLong("pastaAttrsTime");
			}

			expectedId = -1;
//				try {
//					Thread.sleep(10);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
			notifyAll();
		}
//		return null;
	}

	public void printReport() {
//		System.out.println("Performed " + measurements.numSamples + " requests");
//		final double totalMs = requestTimeBuffer / 1_000_000.0;
//		System.out.printf("It took %.1f ms\n", measurements.totalTime);
		System.out.printf("Averages (milliseconds)\n");
		System.out.printf("              E2E: %.2f\n", measurements.getAverageEndToEnd() / 1_000_000.0);
		System.out.printf("Server side total: %.2f\n", measurements.getAverageTotalTime() / 1_000_000.0);
		System.out.printf("  AST parse/flush: %.2f\n", measurements.getAverageParseTime() / 1_000_000.0);
		System.out.printf("   Create locator: %.2f\n", measurements.getAverageCreateLocatorTime() / 1_000_000.0);
		System.out.printf("    Apply locator: %.2f\n", measurements.getAverageApplyLocatorTime() / 1_000_000.0);
		System.out.printf("        Attr eval: %.2f\n", measurements.getAverageAttrEvalTime() / 1_000_000.0);
		System.out.printf("       NodesAtPos: %.2f\n", measurements.getAverageNodesAtPositionTime() / 1_000_000.0);
		System.out.printf("       PastaAttrs: %.2f\n", measurements.getAveragePastaAttrsTime() / 1_000_000.0);

	}

	private String generateSourceFile() {
		return "import java.util.HashSet;\n" + //
				"class Foo {\n" + //
				"  void bar() {\n" + //
				"    HashSet<String> obj = new HashSet<String>(); obj.add(\"foo\" + Math.random()); \n" + //
				"} // " + Math.random() + "\n}\n";
	}

	private void sendMessageAndWaitForResponse(int numProbes) throws InterruptedException {

		final String sourceFile = generateSourceFile();

		final int probeOffset = (int) (Math.random() * numProbes);
		for (int probe = 0; probe < numProbes; ++probe) {

			synchronized (this) {
				expectedId = (int) (Math.random() * Integer.MAX_VALUE);
			}

			final JSONObject msgObj;

			switch (ACTION_TYPE) {
			case CREATE_PROBE:
				sendMessageAndWaitForResponse(ExtendJQueries.createListNodes(expectedId, sourceFile));
				final JSONArray searchResults = lastResult.getJSONObject("result").getJSONArray("nodes");

//				System.out.println(lastResult);
				synchronized (this) {
					expectedId = (int) (Math.random() * Integer.MAX_VALUE);
				}
				msgObj = ExtendJQueries.createListProperties(expectedId, sourceFile,
						searchResults.getJSONObject((probe + probeOffset) % searchResults.length()));
				break;

			case FULL_PARSE_HOT: {
				msgObj = ExtendJQueries.createGetNumChild(expectedId, sourceFile, new JSONArray());
				msgObj.put("cache", "NONE");
				break;
			}

			case FULL_PARSE_COLD: {
				msgObj = ExtendJQueries.createGetNumChild(expectedId, sourceFile, new JSONArray());
				msgObj.put("cache", "PURGE");
				break;
			}

			case EVALUATE_PROBE:
			default: {

				// Perform random lightweight query
				msgObj = evalCases[(probe + probeOffset) % evalCases.length].getQuery(sourceFile);
				break;
			}
			}

			sendMessageAndWaitForResponse(msgObj);
		}
	}

	private void sendMessageAndWaitForResponse(JSONObject msgObj) throws InterruptedException {
		final String outgoingStr = msgObj.toString();
		lastSentMessage = outgoingStr;
//		System.out.println("Sending " + outgoingStr);

		requestStartNanos = System.nanoTime();
		postMessage.accept(outgoingStr);

		synchronized (this) {
			while (expectedId != -1) {
				wait();
			}
		}
	}

	private String lastSentMessage;

	@Override
	public void run() throws InterruptedException {
		if (ACTION_TYPE == ActionType.EVALUATE_PROBE) {
			for (EvalTestCase etc : evalCases) {
				etc.prepare();
			}
		}

		if (ACTION_TYPE.requiresWarmup()) {
			warmup();
		} else {
			System.out.println("Skipping warmup because action type is " + ACTION_TYPE);
		}

		measurements.reset();
		System.out.println("Benchmarking, performing " + NUM_ITERATIONS + " iterations and measuring time usage");
		for (int i = 0; i < NUM_ITERATIONS; i++) {
			if (i != 0 && i % 100 == 0) {
				System.out.println("Iteration " + i);
				if (i % 500 == 0) {
					System.out.println("\n -- Measurements after " + measurements.numSamples + " requests:");
					printReport();
					System.out.println();
				}
			}
			sendMessageAndWaitForResponse(NUM_PROBES);
		}

		System.out.println("\n -- Final measurements after " + measurements.numSamples + " requests:");
		printReport();

		JSONObject job = new JSONObject();
		job.put("end_to_end", measurements.getAverageEndToEnd() / 1_000_000.0);
		job.put("server_side_total", measurements.getAverageTotalTime() / 1_000_000.0);
		job.put("server_side_parse", measurements.getAverageParseTime() / 1_000_000.0);
		job.put("server_side_create_locator", measurements.getAverageCreateLocatorTime() / 1_000_000.0);
		job.put("server_side_apply_locator", measurements.getAverageApplyLocatorTime() / 1_000_000.0);
		job.put("server_side_attr_eval", measurements.getAverageAttrEvalTime() / 1_000_000.0);
		job.put("server_side_nodes_at_position", measurements.getAverageNodesAtPositionTime() / 1_000_000.0);
		job.put("server_side_pasta_attrs", measurements.getAveragePastaAttrsTime() / 1_000_000.0);

		System.out.println(job.toString());
		final String savePath = System.getProperty("benchmark.output");
		if (savePath != null) {
			try {
				System.out.println("Writing result to " + savePath);
				Files.writeString(new File(savePath).toPath(), job.toString());
			} catch (IOException e) {
				System.out.println("Error when writing benchmark result");
				e.printStackTrace();
			}
		}
		System.out.println("Done!");

	}

	private void warmup() throws InterruptedException {
		// Warm up for multiples of N iterations until the standard deviation goes
		// below a threshold
		double prevBestCov = Double.MAX_VALUE;
		int numSequentialInsubstantialIterations = 0;
		final double[] requestTimes = new double[WARMUP_CYCLE_ITERATION_COUNT];
		System.out.println("Warming up...");
		System.out.println("Performing cycles of " + requestTimes.length
				+ " requests until 'Coefficient of Variation' (CoV) goes below " + COV_WARMUP_THRESHOLD);
		while (true) {

			double reqSum = 0;
			for (int i = 0; i < requestTimes.length; i++) {
//				requestTimeBuffer = 0;
				measurements.reset();
				sendMessageAndWaitForResponse(5);
				reqSum += measurements.totalTime;
				requestTimes[i] = measurements.totalTime;
			}
			Arrays.sort(requestTimes);
			final double mean = requestTimes[requestTimes.length / 2];
			final double average = reqSum / requestTimes.length;
			double variance = 0;
			for (int i = 0; i < requestTimes.length; i++) {
				variance += Math.pow(average - requestTimes[i], 2);
			}
			variance /= requestTimes.length;
			final double stdErr = Math.sqrt(variance);
			final double cov = stdErr / mean;
			System.out.printf("CoV: %.3f | stdErr: %.3fms\n", cov, stdErr / 1_000_000.0);
			if (cov <= COV_WARMUP_THRESHOLD) {
				break;
			}
			if (cov < prevBestCov) {
				prevBestCov = cov;
				numSequentialInsubstantialIterations = 0;
			} else {
				++numSequentialInsubstantialIterations;
				if (numSequentialInsubstantialIterations >= COV_WARMUP_IMPLIED_STEADY_STATE_TIMEOUT) {
					System.out.println("Had " + numSequentialInsubstantialIterations
							+ " consecutive cycles that didn't improve CoV");
					System.out.println(
							"It seems that we have reached steady state without reaching the desired CoV threshold.");
					System.out.println("Stopping the warmup");
					break;
				}
			}
		}
	}
}
