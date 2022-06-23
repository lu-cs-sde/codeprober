package benchmark;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

public class ExtendJBenchmark extends BaseBenchmark {

	private static enum ProbeType {
		MINI, PASTA, COMMONSCODEC, NETBEANS, PMD, FOP,
	};

	private static final int NUM_ITERATIONS;
	private static final int NUM_PROBES;
	private static final ProbeType PROBE_TYPE;

	private static final double COV_WARMUP_THRESHOLD;
	private static final int COV_WARMUP_IMPLIED_STEADY_STATE_TIMEOUT;

	static {
		NUM_ITERATIONS = Integer.parseInt(System.getProperty("benchmark.iterations", "1000"));
		NUM_PROBES = Integer.parseInt(System.getProperty("benchmark.probes", "1"));
		PROBE_TYPE = ProbeType.valueOf(System.getProperty("benchmark.probeType", "mini").toUpperCase(Locale.ENGLISH));
		COV_WARMUP_THRESHOLD = Double.parseDouble(System.getProperty("benchmark.warmupCovThreshold", "0.01"));
		COV_WARMUP_IMPLIED_STEADY_STATE_TIMEOUT = Integer
				.parseInt(System.getProperty("benchmark.warmupImpliedSteadyStateTimeout", "10"));
	}

	private static class Measurement {
		public int numSamples;
		public long endToEndTime;
		public long totalTime;
		public long parseTime;
		public long createLocatorTime;
		public long applyLocatorTime;
		public long attrEvalTime;

		public void reset() {
			numSamples = 0;
			endToEndTime = 0;
			parseTime = 0;
			totalTime = 0;
			createLocatorTime = 0;
			applyLocatorTime = 0;
			attrEvalTime = 0;
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
	}

	private int expectedId = -1;
	private long requestStartNanos;
	private Measurement measurements = new Measurement();

	public ExtendJBenchmark(Consumer<String> postMessage) {
		super(postMessage);
		// TODO Auto-generated constructor stub
	}

//	private Set<String> uniqResponses = new HashSet<>();
	@Override
	public void handleIncomingMessage(JSONObject msg) {
		synchronized (this) {
			final int actualid = msg.getInt("id");
//			System.out.println("Got msg with id " + actualid +", was waiting for " + expectedId);
						if (actualid != expectedId) {
				System.out.println("Unknown RPC response: " + msg.toString());
				System.out.println("Expected id: " + expectedId);
				return;
			}
			final JSONObject res = msg.getJSONObject("result");
			if (!res.has("locator")) {
				System.out.println("Got response without locator - query or test config is probably wrong");
				return;
			}
//			final String msgStr = "" + msg.getJSONObject("result").get("body");
//			if (!uniqResponses.contains(msgStr)) {
//				uniqResponses.add(msgStr);
//				System.out.println("UNIQ resp: " + msgStr +"  | from " + lastSentMessage);
//			}

			measurements.numSamples++;
			measurements.endToEndTime += System.nanoTime() - requestStartNanos;

			measurements.totalTime += res.getLong("totalTime");
			measurements.parseTime += res.getLong("parseTime");
			measurements.createLocatorTime += res.getLong("createLocatorTime");
			measurements.applyLocatorTime += res.getLong("applyLocatorTime");
			measurements.attrEvalTime += res.getLong("attrEvalTime");

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
	}

	private String generateSourceFile() {
		return "import java.util.HashSet;\n\nclass Foo {\n  void bar() {\n" + //
//				"pasta.PastaServer.printUsage(); \n" + //
				"HashSet<String> obj = new HashSet<String>(); obj.add(\"foo\" + Math.random()); \n" + //
				"} // " + Math.random() + "\n}\n";
	}

	private void sendMessageAndWaitForResponse(int numProbes) throws InterruptedException {
		final String sourceFile = generateSourceFile();

		final int probeOffset = (int) (Math.random() * 5);
		for (int probe = 0; probe < numProbes; ++probe) {

			synchronized (this) {
				expectedId = (int) (Math.random() * Integer.MAX_VALUE);
			}

			final JSONObject msgObj;

			switch ((probe + probeOffset) % 5) {
			case 0:
				msgObj = ExtendJQueries.createStdJavaLibQuery(expectedId, sourceFile);
				break;
			case 1:
				msgObj = ExtendJQueries.createIsEnumDecl(expectedId, sourceFile, new JSONArray() //
						.put(ExtendJQueries.createLookupTypeDeclForBenchmarkStep(25)) //
				);
				break;
			case 2:
				msgObj = ExtendJQueries.createGetNumChild(expectedId, sourceFile, new JSONArray() //
						.put(ExtendJQueries.createLookupTypeDeclForBenchmarkStep(100)) // = Our file, it is always last
																						// (=100)
						.put(ExtendJQueries.createTALStep("StringLiteral", (5 << 12), (6 << 12))) //
				);
				break;
			case 3:
				msgObj = ExtendJQueries.createGetNumChild(expectedId, sourceFile, new JSONArray() //
						.put(ExtendJQueries.createLookupTypeDeclForBenchmarkStep(50)) //
						.put(ExtendJQueries.createTALStep("MethodDecl", (1 << 12), (128 << 12))) //
				);
				break;
			case 4:
			default:
//				msgObj = ExtendJQueries.createTAL(expectedId, sourceFile, "MethodDecl", (4 << 12), (5 << 12));

				msgObj = ExtendJQueries.createGetNumChild(expectedId, sourceFile, new JSONArray() //
						.put(ExtendJQueries.createLookupTypeDeclForBenchmarkStep(75)) //
				);
				break;
			}

			final String outgoingStr = msgObj.toString();
//			lastSentMessage = outgoingStr;
//			System.out.println("Sending " + outgoingStr);

			requestStartNanos = System.nanoTime();
			postMessage.accept(outgoingStr);

			synchronized (this) {
				while (expectedId != -1) {
					wait();
				}
			}
		}
	}
	
//	private String lastSentMessage;

	@Override
	public void run() throws InterruptedException {
		// Warm up for multiples of 100 iterations until the standard deviation goes
		// below a threshold
		double prevBestCov = Double.MAX_VALUE;
		int numSequentialInsubstantialIterations = 0;
		final double[] requestTimes = new double[100];
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
}
