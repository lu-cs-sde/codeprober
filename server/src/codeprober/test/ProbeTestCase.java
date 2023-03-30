package codeprober.test;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.DefaultRequestHandler;
import codeprober.TestClient;
import codeprober.TestClient.TestResult;
import codeprober.toolglue.UnderlyingTool;

public class ProbeTestCase implements Comparable<ProbeTestCase> {

	final TestClient client;
	final JSONObject data;

	public ProbeTestCase(TestClient client, JSONObject data) {
		this.client = client;
		this.data = data;
	}

	private String src() {
		return data.optString("src", "<malformed test: src missing>");
	}

	public String name() {
		return data.optString("name", "<malformed test: name missing>");
	}

	@Override
	public int compareTo(ProbeTestCase o) {
		final String our = src();
		final String their = o.src();
		if (our != their) {
			return our.compareTo(their);
		}
		return name().compareTo(o.name());
	}


	public void assertPass() {
		final TestResult result = client.runTest(data);
		if (result.pass) {
			return;
		}
		final StringBuilder msg = new StringBuilder();
		msg.append("Failed test '" + name() + "'");
		msg.append("\nExpected output:");
		for (int i = 0; i < result.expected.length(); ++i) {
			msg.append("\n> " + result.expected.get(i));
		}
		msg.append("\nActual output:");
		for (int i = 0; i < result.actual.length(); ++i) {
			msg.append("\n> " + result.actual.get(i));
		}
		msg.append("\nOpen the test in CodeProber for a more detailed error report");
		msg.append("\nFull log below:");
		for (String line : result.stdoutLinesDuringParsing) {
			msg.append("\n> " + line);
		}

		fail(msg.toString());
	}

	@Override
	public String toString() {
		return name();
	}

	public static List<ProbeTestCase> listTestsInFile(UnderlyingTool tool, File file) throws IOException {
		final TestClient client = new TestClient(new DefaultRequestHandler(tool));

		final byte[] data = Files.readAllBytes(file.toPath());
		final JSONArray arr = new JSONArray(new String(data, 0, data.length, StandardCharsets.UTF_8));

		final List<ProbeTestCase> ret = new ArrayList<>();
		for (int i = 0; i < arr.length(); ++i) {
			ret.add(new ProbeTestCase(client, arr.getJSONObject(i)));
		}
		ret.sort(ProbeTestCase::compareTo);
		return ret;
	}
}
