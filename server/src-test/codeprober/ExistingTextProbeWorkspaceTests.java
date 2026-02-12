package codeprober;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import codeprober.toolglue.UnderlyingTool;
import codeprober.util.ASTProvider;

@RunWith(Parameterized.class)
public class ExistingTextProbeWorkspaceTests extends ExistingTextProbeTest {

	@Parameters(name = "{0}")
	public static Iterable<TextProbeFile> data() throws IOException {
		// Clear cache in case the "future" version of this test class ran first
		ASTProvider.purgeCache();
		return ExistingTextProbeTest.listTests( //
				new File("../textprobe/workspace"), //
				UnderlyingTool.fromJar("../textprobe/textprobe.jar"), //
				".tp" //
		) //
				.stream() //
				.filter(x -> !x.fullPath.startsWith("future_syntax")) //
				.collect(Collectors.toList());
	}

	public ExistingTextProbeWorkspaceTests(TextProbeFile tc) {
		super(tc);
	}

	@Test
	@Override
	public void run() {
		super.run();
	}
}
