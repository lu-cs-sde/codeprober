package codeprober;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import codeprober.test.WorkspaceTestCase;
import codeprober.textprobe.TextProbeEnvironment;
import codeprober.toolglue.UnderlyingTool;

@RunWith(Parameterized.class)
public class RunAddNumTests {

	@Parameters(name = "{0}")
	public static Iterable<WorkspaceTestCase> data() throws IOException {
		return WorkspaceTestCase.listTextProbesInWorkspace( //
				UnderlyingTool.fromJar("../addnum/AddNum.jar"), //
				new File("../addnum/workspace"));
	}

	private final WorkspaceTestCase tc;

	public RunAddNumTests(WorkspaceTestCase tc) {
		this.tc = tc;
	}

	@Test
	public void run() {
		System.setProperty(TextProbeEnvironment.autoLabelPropertiesKey, "true");
		if (tc.getSrcFilePath().contains("err_")) {
			if (tc.isVarDecl()) {
				tc.assertPass();
			} else {
				tc.assertFail();
			}
		} else {
			tc.assertPass();
		}
	}

	@After
	public void restore() {
		System.clearProperty(TextProbeEnvironment.autoLabelPropertiesKey);
	}
}
