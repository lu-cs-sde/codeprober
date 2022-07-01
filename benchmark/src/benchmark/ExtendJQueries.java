package benchmark;

import org.json.JSONArray;
import org.json.JSONObject;

public class ExtendJQueries {

	private static void generateBusyLocatorStep(JSONArray out) {
		out.put(new JSONObject() //
				.put("type", "nta") //
				.put("value", new JSONObject() //
						.put("name", "getChild") //
						.put("args", new JSONArray() //
								.put(new JSONObject() //
										.put("type", "int") //
										.put("isNodeType", false) //
										.put("value", 0) //
								))));
		out.put(new JSONObject() //
				.put("type", "nta") //
				.put("value", new JSONObject() //
						.put("name", "getParent") //
						.put("args", new JSONArray())));
	}

	// + Prob för Program.errors() (eller CompilationUnit.errors())

	private static JSONObject createLookupTypeStep(String pkg, String name) {
		return new JSONObject() //
				.put("type", "nta") //
				.put("value", new JSONObject() //
						.put("name", "lookupType") //
						.put("args", new JSONArray() //
								.put(new JSONObject() //
										.put("type", "java.lang.String") //
										.put("isNodeType", false) //
										.put("value", pkg) //
								) //
								.put(new JSONObject() //
										.put("type", "java.lang.String") //
										.put("isNodeType", false) //
										.put("value", name) //
								) //
						) //
				);

	}

	public static JSONObject createLookupTypeDeclForBenchmarkStep(int posSelector) {
		return new JSONObject() //
				.put("type", "nta") //
				.put("value", new JSONObject() //
						.put("name", "getTypeDeclForBenchmark") //
						.put("args", new JSONArray() //
								.put(new JSONObject() //
										.put("type", "int") //
										.put("isNodeType", false) //
										.put("value", posSelector) //
								) //
						) //
				);

	}

	public static JSONObject createTALStep(String type, int start, int end) {
		return new JSONObject() //
				.put("type", "tal") //
				.put("value", new JSONObject() //
						.put("type", type) //
						.put("start", start) //
						.put("end", end) //
						.put("depth", 123) //
				);

	}

	private static JSONObject createBaseMessageObject(int rpcId, String sourceFile) {
		final JSONObject msgObj = new JSONObject();
		msgObj.put("id", rpcId);
		msgObj.put("posRecovery", "FAIL");
		msgObj.put("cache", "FULL");
		msgObj.put("type", "query");
		msgObj.put("text", sourceFile);
		msgObj.put("stdout", false);
		return msgObj;
	}

	public static JSONObject createStdJavaLibQuery(int rpcId, String sourceFile) {

		JSONArray locatorSteps = new JSONArray();
//		for (int i = 0; i < 10; i++) {
//			generateBusyLocatorStep(locatorSteps);
//		}
		locatorSteps.put(createLookupTypeStep("java.lang", "Object"));

		final JSONObject msgObj = createBaseMessageObject(rpcId, sourceFile);
		msgObj.put("query", new JSONObject() //
				.put("attr", new JSONObject() //
						.put("name", "unqualifiedLookupMethod") //
						.put("args", new JSONArray() //
								.put(new JSONObject() //
										.put("type", "java.lang.String") //
										.put("isNodeType", false) //
										.put("value", "hashCode") //
								) //
						) //
				) //
				.put("locator", new JSONObject() //
						.put("steps", locatorSteps)));

		return msgObj;
	}

	public static JSONObject createLookupExternalTypeQuery(int rpcId, String sourceFile, String lookupPkg,
			String lookupName) {
		return createBaseMessageObject(rpcId, sourceFile) //
				.put("query", new JSONObject() //
						.put("attr", new JSONObject() //
								.put("name", "isEnumDecl") //
						) //
						.put("locator", new JSONObject() //
								.put("steps", new JSONArray() //
										.put(createLookupTypeStep(lookupPkg, lookupName)))));
	}

	public static JSONObject createNodesAtPosition(int rpcId, String sourceFile) {
		return createBaseMessageObject(rpcId, sourceFile) //
				.put("query", new JSONObject() //
						.put("attr", new JSONObject() //
								.put("name", "pasta_spansAndNodeTypes") //
						) //
						.put("locator", new JSONObject() //
								.put("steps", new JSONArray()) //
								.put("result", new JSONObject() //
										// Line 16, column 12
										.put("start", (4 << 12) + 60) //
										.put("end", (4 << 12) + 60) //
								) //
						));
	}

	public static JSONObject createPastaAttrs(int rpcId, String sourceFile, JSONObject locator) {
		return createBaseMessageObject(rpcId, sourceFile) //
				.put("query", new JSONObject() //
						.put("attr", new JSONObject() //
								.put("name", "pasta_pastaAttrs") //
						) //
						.put("locator", locator) //
						);
	}

	public static JSONObject createGetNumChild(int rpcId, String sourceFile, JSONArray steps) {
		return createBaseMessageObject(rpcId, sourceFile) //
				.put("query", new JSONObject() //
						.put("attr", new JSONObject() //
								.put("name", "getNumChild") //
						) //
						.put("locator", new JSONObject() //
								.put("steps", steps)));
	}

	public static JSONObject createIsEnumDecl(int rpcId, String sourceFile, JSONArray steps) {
		return createBaseMessageObject(rpcId, sourceFile) //
				.put("query", new JSONObject() //
						.put("attr", new JSONObject() //
								.put("name", "isEnumDecl") //
						) //
						.put("locator", new JSONObject() //
								.put("steps", steps)));
	}

	public static JSONObject createLookupTypeDeclForBenchmark(int rpcId, String sourceFile, int posSelector) {
		return createBaseMessageObject(rpcId, sourceFile) //
				.put("query", new JSONObject() //
						.put("attr", new JSONObject() //
								.put("name", "isEnumDecl") //
						) //
						.put("locator", new JSONObject() //
								.put("steps", new JSONArray() //
										.put(createLookupTypeDeclForBenchmarkStep(posSelector)))));
	}

	public static JSONObject createTAL(int rpcId, String sourceFile, String talType, int talStart, int talEnd) {
		return createBaseMessageObject(rpcId, sourceFile) //
				.put("query", new JSONObject() //
						.put("attr", new JSONObject() //
								.put("name", "getNumChild") //
						) //
						.put("locator", new JSONObject() //
								.put("steps", new JSONArray() //
										.put(createTALStep(talType, talStart, talEnd)))));

	}
}
