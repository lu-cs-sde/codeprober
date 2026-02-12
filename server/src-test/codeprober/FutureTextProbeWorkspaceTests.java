package codeprober;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import codeprober.textprobe.Parser;
import codeprober.toolglue.UnderlyingTool;
import codeprober.util.ASTProvider;

@RunWith(Parameterized.class)
public class FutureTextProbeWorkspaceTests extends ExistingTextProbeTest {

	static String propKey = "cpr.permitImplicitStringConversion";
	static String oldPropVal = System.getProperty("cpr.permitImplicitStringConversion");

	@Parameters(name = "{0}")
	public static Iterable<TextProbeFile> data() throws IOException {
		final boolean wasPermitted = Parser.permitImplicitStringConversion;
		System.setProperty(propKey, "false");
		// Clear cache in case the non-future version of this test class ran first
		ASTProvider.purgeCache();
		try {
			Parser.permitImplicitStringConversion = false;
			return ExistingTextProbeTest.listTests( //
					new File("../textprobe/workspace"), //
					UnderlyingTool.fromJar("../textprobe/textprobe.jar"), //
					".tp" //
			) //
					.stream() //
					.filter(x -> x.fullPath.startsWith("future_syntax")) //
					.collect(Collectors.toList());
		} finally {
			Parser.permitImplicitStringConversion = wasPermitted;
		}
	}

	public FutureTextProbeWorkspaceTests(TextProbeFile tc) {
		super(tc);
	}

	@AfterClass
	public static void restore() {
		if (oldPropVal == null) {
			System.clearProperty(propKey);
		} else {
			System.setProperty(propKey, oldPropVal);
		}
	}

	@Test
	@Override
	public void run() {
		super.run();
	}
}
