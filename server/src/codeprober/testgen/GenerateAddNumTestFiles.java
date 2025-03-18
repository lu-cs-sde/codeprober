package codeprober.testgen;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import codeprober.protocol.AstCacheStrategy;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.protocol.TestCaseAssertType;
import codeprober.protocol.data.FNStep;
import codeprober.protocol.data.NestedTest;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.NodeLocatorStep;
import codeprober.protocol.data.ParsingRequestData;
import codeprober.protocol.data.ParsingSource;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.TALStep;
import codeprober.protocol.data.TestCase;
import codeprober.protocol.data.TestSuite;

/**
 * Utility class for generating tests for the 'AddNum' tool (see addnum dir in
 * top of repository). Used for testing the built CodeProber jar file.
 *
 */
public class GenerateAddNumTestFiles {

	public static void main(String[] args) throws Exception {
		final String dstFile = System.getProperty("DST_JSON");
		if (dstFile == null) {
			throw new Error("Missing destination file property 'DST_JSON'");
		}
		if (!new File(dstFile).getParentFile().exists()) {
			throw new Error("Invalid destination file property 'DST_JSON'");
		}

		final List<TestCase> cases = new ArrayList<>();

		cases.add(genNumVal());
		cases.add(genNumPrettyPrint());
		cases.add(genAdd1and2());
		cases.add(genPrettyPrintNestedAdd());
		cases.add(genPrettyPrintToStream());
		cases.add(genNested());
		cases.add(genIntentionalTestFailure());
		cases.add(genSubtypeSearch());

		Files.write(new File(dstFile).toPath(),
				new TestSuite(1, cases).toJSON().toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
	}

	private static ParsingRequestData src(String text) {
		return new ParsingRequestData(PositionRecoveryStrategy.ALTERNATE_PARENT_CHILD, AstCacheStrategy.PARTIAL,
				ParsingSource.fromText(text), Collections.emptyList(), ".addnum");
	}

	private static NodeLocator root() {
		return new NodeLocator(new TALStep("addnum.ast.Program", null, 0, 0, 0, null), Collections.emptyList());
	}

	private static TestCase simpleRootTest(String name, String text, Property property, String expectedOutput) {
		return simpleRootTest(name, text, property, Arrays.asList(RpcBodyLine.fromPlain(expectedOutput)));
	}

	private static TestCase simpleRootTest(String name, String text, Property property,
			List<RpcBodyLine> expectedOutput) {
		return new TestCase(name, src(text), property, root(), TestCaseAssertType.IDENTITY, expectedOutput,
				Collections.emptyList());
	}

	private static TestCase genNumVal() {
		return simpleRootTest("123.value()", "123", new Property("value", Collections.emptyList(), null), "123");
	}

	private static TestCase genNumPrettyPrint() {
		return simpleRootTest("123.prettyPrint()", "123", new Property("prettyPrint", Collections.emptyList(), null),
				"123");
	}

	private static TestCase genAdd1and2() {
		return simpleRootTest("(1+2).value()", "1 + 2", new Property("value", Collections.emptyList(), null), "3");
	}

	private static TestCase genPrettyPrintNestedAdd() {
		return simpleRootTest("(1 + 2 + 3).prettyPrint", "1 + 2 + 3",
				new Property("prettyPrint", Collections.emptyList(), null), "((1 + 2) + 3)");
	}

	private static NodeLocator childLocator(String type, int start, int end, Integer... childIndexes) {
		return new NodeLocator(new TALStep("addnum.ast.Num", null, start, end, childIndexes.length, false),
				Arrays.asList(childIndexes).stream().map(NodeLocatorStep::fromChild).collect(Collectors.toList()));
	}

	private static TestCase genSubtypeSearch() {
		return simpleRootTest("Subtype search", "1 + 2 + 3", new Property("m:NodesWithProperty", Arrays.asList( //
				PropertyArg.fromString(""), PropertyArg.fromString("this <: Num")), null),
				Arrays.asList(RpcBodyLine.fromArr(Arrays.asList( //
						RpcBodyLine.fromPlain("Found 3 nodes"), //
						RpcBodyLine.fromNode(childLocator("addnum.ast.Num", (1 << 12) + 1, (1 << 12) + 1, 0, 0, 0)),
						RpcBodyLine.fromPlain("\n"), //
						RpcBodyLine.fromNode(childLocator("addnum.ast.Num", (1 << 12) + 5, (1 << 12) + 5, 0, 0, 1)),
						RpcBodyLine.fromPlain("\n"), //
						RpcBodyLine.fromNode(childLocator("addnum.ast.Num", (1 << 12) + 9, (1 << 12) + 9, 0, 1)), //
						RpcBodyLine.fromPlain("\n") //
				))));
	}

	private static TestCase genPrettyPrintToStream() {
		return new TestCase("123.prettyPrint(PrintStream)", src("123"),
				new Property("prettyPrint", Arrays.asList(PropertyArg.fromOutputstream(PrintStream.class.getName())),
						null),
				root(), TestCaseAssertType.IDENTITY, Arrays.asList(RpcBodyLine.fromStreamArg("123")),
				Collections.emptyList());
	}

	private static TestCase genNested() {
		final TALStep innerAddStep = new TALStep("addnum.ast.Add", null, (1 << 12) + 6, (1 << 12) + 10, 2, false);
		final NodeLocator asNumLocator = new NodeLocator(
				new TALStep("addnum.ast.Num", null, innerAddStep.start, innerAddStep.end, 3, false), Arrays.asList( //
						// This uses a naive locator since it is part of the probe output
						NodeLocatorStep.fromChild(0), // Program -> root add
						NodeLocatorStep.fromChild(1), // root Add -> inner add
						NodeLocatorStep.fromNta(new FNStep(new Property("asNum", Collections.emptyList(), null))) //
				));

		final List<NestedTest> nested = new ArrayList<>();
		nested.add(new NestedTest(Arrays.asList(0), //
				new Property("value", Collections.emptyList(), null), //
				Arrays.asList(RpcBodyLine.fromPlain("5")), //
				Collections.emptyList()));

		return new TestCase("(1 + 2 + 3).asNum().prettyPrint()", src("1 + (2 + 3)"),
				new Property("asNum", Collections.emptyList(), null), //
				new NodeLocator(innerAddStep, Arrays.asList(NodeLocatorStep.fromTal(innerAddStep))),
				TestCaseAssertType.IDENTITY, Arrays.asList( //
						RpcBodyLine.fromNode(asNumLocator), //
						RpcBodyLine.fromPlain("\n"), //
						RpcBodyLine.fromStdout("Stdout line that shouldn't affect the test comparison") //
				), nested);

	}

	private static TestCase genIntentionalTestFailure() {
		return simpleRootTest("(1+3).value() - intentionally created to fail", "1 + 2",
				new Property("value", Collections.emptyList(), null), "5");
	}
}
