package codeprober.test;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import codeprober.DefaultRequestHandler;
import codeprober.TestClient;
import codeprober.TestClient.TestResult;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.TestCase;
import codeprober.protocol.data.TestSuite;
import codeprober.toolglue.UnderlyingTool;

public class ProbeTestCase implements Comparable<ProbeTestCase> {

	final TestClient client;
	final TestCase data;

	public ProbeTestCase(TestClient client, TestCase data) {
		this.client = client;
		this.data = data;
	}

	private String src() {
		return data.src.text;
	}

	public String name() {
		return data.name;
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

	private static String convertRpcLineToHumanReadable(RpcBodyLine line) {
		switch (line.type) {
		case plain:
			return line.asPlain();

		default:
			return line.toJSON().toString();
		}
	}

	public void assertPass() {
		final TestResult result = client.runTest(data);
		if (result.pass) {
			return;
		}
		final StringBuilder msg = new StringBuilder();
		msg.append("Failed test '" + name() + "'");
		if (result.whereInNestedPropertyIfAny.length() > 0) {
			msg.append(" -> " + result.whereInNestedPropertyIfAny);
		}
		msg.append("\nExpected output:");
		for (int i = 0; i < result.expected.size(); ++i) {
			msg.append("\n> " + convertRpcLineToHumanReadable(result.expected.get(i)));
		}
		msg.append("\nActual output:");
		for (int i = 0; i < result.actual.size(); ++i) {
			msg.append("\n> " + convertRpcLineToHumanReadable(result.actual.get(i)));
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
		final JSONObject suiteObj = new JSONObject(new String(data, 0, data.length, StandardCharsets.UTF_8));
		// TODO if suiteObj.getInt("v") is not current version, upgrade the file here
		// perhaps
		final TestSuite suite = TestSuite.fromJSON(suiteObj);

		final List<ProbeTestCase> ret = new ArrayList<>();
		for (int i = 0; i < suite.cases.size(); ++i) {
			ret.add(new ProbeTestCase(client, suite.cases.get(i)));
		}
		ret.sort(ProbeTestCase::compareTo);
		return ret;
	}
}
