package codeprober;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import codeprober.textprobe.TextProbeEnvironment;
import codeprober.toolglue.UnderlyingTool;

@RunWith(Parameterized.class)
public class ExistingAddNumWorkspaceTests extends ExistingTextProbeTest {

	@Parameters(name = "{0}")
	public static Iterable<TextProbeFile> data() throws IOException {
		return ExistingTextProbeTest.listTests(new File("../addnum/workspace"),
				UnderlyingTool.fromJar("../addnum/AddNum.jar"), ".addn");
	}

	public ExistingAddNumWorkspaceTests(TextProbeFile tc) {
		super(tc);
	}

	@Test
	@Override
	public void run() {
		System.setProperty(TextProbeEnvironment.autoLabelPropertiesKey, "true");
		super.run();
	}

	@After
	public void restore() {
		System.clearProperty(TextProbeEnvironment.autoLabelPropertiesKey);
	}
}
