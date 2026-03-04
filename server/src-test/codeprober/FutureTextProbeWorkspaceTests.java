package codeprober;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

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

	@Parameters(name = "{0}")
	public static Iterable<TextProbeFile> data() throws IOException {
		try {
			// Disable implicit conversion
			install();
			// Clear cache in case the non-future version of this test class ran first
			ASTProvider.purgeCache();
			return ExistingTextProbeTest.listTests( //
					new File("../textprobe/workspace"), //
					UnderlyingTool.fromJar("../textprobe/textprobe.jar"), //
					".tp" //
			) //
					.stream() //
					.filter(x -> x.fullPath.startsWith("future_syntax")) //
					.collect(Collectors.toList());
		} finally {
			restore();
			ASTProvider.purgeCache();
		}
	}

	public FutureTextProbeWorkspaceTests(TextProbeFile tc) {
		super(tc);
	}

	private static void install() {
		Parser.permitImplicitStringConversion = false;
		System.setProperty(propKey, "false");
	}

	private static void restore() {
		Parser.permitImplicitStringConversion = true;
		System.setProperty(propKey, "true");
	}

	@Test
	@Override
	public void run() {
		super.run();
	}
}
