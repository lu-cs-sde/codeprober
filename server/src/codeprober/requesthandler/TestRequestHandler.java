package codeprober.requesthandler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import codeprober.protocol.GetTestSuiteContentsErrorCode;
import codeprober.protocol.ListTestSuitesErrorCode;
import codeprober.protocol.PutTestSuiteContentsErrorCode;
import codeprober.protocol.data.GetTestSuiteReq;
import codeprober.protocol.data.GetTestSuiteRes;
import codeprober.protocol.data.ListTestSuitesReq;
import codeprober.protocol.data.ListTestSuitesRes;
import codeprober.protocol.data.PutTestSuiteReq;
import codeprober.protocol.data.PutTestSuiteRes;
import codeprober.protocol.data.TestSuite;
import codeprober.protocol.data.TestSuiteListOrError;
import codeprober.protocol.data.TestSuiteOrError;

public class TestRequestHandler {

	static String getTestDir() {
		return System.getProperty("cpr.testDir");
	}

	public static ListTestSuitesRes list(ListTestSuitesReq req) {
		final String testDir = getTestDir();
		if (testDir != null) {
			File dir = new File(testDir);
			if (dir.isDirectory()) {
				final File[] files = dir.listFiles();
				if (files != null) {
					final List<String> names = new ArrayList<>();
					for (File f : files) {
						final String name = f.getName();
						if (name.endsWith(".json")) {
							names.add(name);
						}
					}
					names.sort(String::compareTo);
					return new ListTestSuitesRes(TestSuiteListOrError.fromSuites(names));
				} else {
					System.err
							.println("Couldn't list contents of TestDir '" + testDir + "', perhaps a permission issue");
					return new ListTestSuitesRes(
							TestSuiteListOrError.fromErr(ListTestSuitesErrorCode.ERROR_WHEN_LISTING_TEST_DIR));
				}
			} else {
				System.err.println("TestDir '" + testDir + "' is not a directory");
				return new ListTestSuitesRes(
						TestSuiteListOrError.fromErr(ListTestSuitesErrorCode.ERROR_WHEN_LISTING_TEST_DIR));
			}
		} else {
			return new ListTestSuitesRes(TestSuiteListOrError.fromErr(ListTestSuitesErrorCode.NO_TEST_DIR_SET));
		}
	}

	public static GetTestSuiteRes get(GetTestSuiteReq req) {
		final String testDir = getTestDir();
		if (testDir != null) {
			File f = new File(testDir, req.suite);
			if (new File(testDir).toPath().startsWith(f.toPath())) {
				System.err.println("Attempted read of file " + f + ", which is outside of testDir " + testDir);
				return new GetTestSuiteRes(
						TestSuiteOrError.fromErr(GetTestSuiteContentsErrorCode.ERROR_WHEN_READING_FILE));
			}
			if (!f.exists()) {
				return new GetTestSuiteRes(TestSuiteOrError.fromErr(GetTestSuiteContentsErrorCode.NO_SUCH_TEST_SUITE));
			}
			try {
				return new GetTestSuiteRes(TestSuiteOrError.fromContents(TestSuite
						.fromJSON(new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)))));
			} catch (JSONException | IOException e) {
				e.printStackTrace();
				System.err.println("Failed reading contents of " + f);
				return new GetTestSuiteRes(TestSuiteOrError.fromErr(GetTestSuiteContentsErrorCode.ERROR_WHEN_READING_FILE));
			}
		} else {
			System.err.println("No TestDir set, cannot get contents");
			return new GetTestSuiteRes(TestSuiteOrError.fromErr(GetTestSuiteContentsErrorCode.NO_TEST_DIR_SET));
		}
	}

	public static PutTestSuiteRes put(PutTestSuiteReq req) {
		final String testDir = getTestDir();
		if (testDir != null) {
			File f = new File(testDir, req.suite);
			if (new File(testDir).toPath().startsWith(f.toPath())) {
				System.err.println("Attempted insertion of file to " + f + ", which is outside of testDir " + testDir);
				return new PutTestSuiteRes(PutTestSuiteContentsErrorCode.ERROR_WHEN_WRITING_FILE);
			}
			try {
				Files.write(f.toPath(), req.contents.toJSON().toString().getBytes(StandardCharsets.UTF_8));
				return new PutTestSuiteRes(null);
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println("Failed reading contents of " + f);
				return new PutTestSuiteRes(PutTestSuiteContentsErrorCode.ERROR_WHEN_WRITING_FILE);
			}
		} else {
			System.err.println("No TestDir set, cannot put contents");
			return new PutTestSuiteRes(PutTestSuiteContentsErrorCode.NO_TEST_DIR_SET);
		}
	}
}
