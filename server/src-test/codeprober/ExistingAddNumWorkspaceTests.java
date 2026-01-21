package codeprober;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import codeprober.toolglue.UnderlyingTool;

@RunWith(Parameterized.class)
public class ExistingAddNumWorkspaceTests extends ExistingTextProbeTest {

	@Parameters(name = "{0}")
	public static Iterable<CompleteTestCase> data() throws IOException {
		return ExistingTextProbeTest.listTests(new File("../addnum/workspace"),
				UnderlyingTool.fromJar("../addnum/AddNum.jar"), ".addn");
	}

	public ExistingAddNumWorkspaceTests(CompleteTestCase tc) {
		super(tc);
	}

	@Test
	@Override
	public void run() {
		super.run();
	}
}
