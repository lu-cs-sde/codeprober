package codeprober;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import codeprober.protocol.ClientRequest;
import codeprober.protocol.data.CompleteReq;
import codeprober.protocol.data.CompleteRes;
import codeprober.protocol.data.CompletionItem;
import codeprober.protocol.data.Decoration;
import codeprober.protocol.data.GetDecorationsReq;
import codeprober.protocol.data.GetDecorationsRes;
import codeprober.protocol.data.ParsingSource;
import codeprober.requesthandler.LazyParser;
import codeprober.requesthandler.WorkspaceHandler;
import codeprober.rpc.JsonRequestHandler;
import codeprober.test.WorkspaceTestCase;
import codeprober.textprobe.Parser;
import codeprober.textprobe.TextProbeEnvironment;
import codeprober.textprobe.ast.ASTNode;
import codeprober.textprobe.ast.ASTNode.TraversalResult;
import codeprober.textprobe.ast.Document;
import codeprober.textprobe.ast.ExpectedValue;
import codeprober.textprobe.ast.ExpectedValue.Type;
import codeprober.textprobe.ast.PropertyAccess;
import codeprober.textprobe.ast.Query;
import codeprober.textprobe.ast.QueryHead;
import codeprober.textprobe.ast.VarDecl;
import codeprober.toolglue.UnderlyingTool;

/**
 * Go through existing text probe files which are known to be valid, assert that
 * our text probe implementation treats them correctly. Two things are tested:
 * <ol>
 * <li>Find parts that *can* be auto-completed, ensure that the auto-completable
 * label is present in the completion list.</li>
 * <li>Make sure the decorations API covers all text probes.</li>
 * </ol>
 */
public abstract class ExistingTextProbeTest {

	public static class TextProbeFile {
		public final String fullPath;
		public final JsonRequestHandler requestHandler;
		public final TextProbeEnvironment env;

		public TextProbeFile(String fullPath, JsonRequestHandler requestHandler, TextProbeEnvironment env) {
			this.fullPath = fullPath;
			this.requestHandler = requestHandler;
			this.env = env;
		}

		@Override
		public String toString() {
			return fullPath + " [x" + env.document.containers.getNumChild() + "]";
		}
	}

	public static Iterable<TextProbeFile> listTests(File workspace, UnderlyingTool tool, String expectedFileSuffix) {
		final WorkspaceHandler wsh = new WorkspaceHandler(workspace);
		final JsonRequestHandler requestHandler = new DefaultRequestHandler(tool, wsh);
		final List<TextProbeFile> ret = new ArrayList<>();
		WorkspaceTestCase.listWorkspaceFilePaths(wsh, null, fullPath -> {
			if (fullPath.contains("err_") || !fullPath.endsWith(expectedFileSuffix)) {
				// Ignore
				return;
			}
			final ParsingSource psrc = ParsingSource.fromWorkspacePath(fullPath);
			final Document doc = Parser.parse(LazyParser.extractText(psrc, wsh), '[', ']');
			ret.add(new TextProbeFile(fullPath, requestHandler,
					new TextProbeEnvironment(requestHandler, wsh, psrc, doc, null, false)));

		});
		return ret;
	}

	private final TextProbeFile tc;

	public ExistingTextProbeTest(TextProbeFile tc) {
		this.tc = tc;
	}

	protected void run() {
		tc.env.document.traverseDescendants(desc -> {
			if (desc instanceof Query) {
				checkQueryCompletion((Query) desc);
				return TraversalResult.SKIP_SUBTREE;
			}
			return TraversalResult.CONTINUE;
		});

		final ClientRequest req = new ClientRequest( //
				new GetDecorationsReq(tc.env.parsingRequestData).toJSON(), (upd) -> {
				}, new AtomicBoolean(false), p -> {
				});
		final GetDecorationsRes res = GetDecorationsRes.fromJSON(tc.requestHandler.handleRequest(req));
		assertNotNull(res.lines);

		tc.env.document.traverseDescendants(desc -> {
			if (desc instanceof Query) {
				assertDecorations(res.lines, desc, "ok", "query");
				return TraversalResult.SKIP_SUBTREE;
			}

			if (desc instanceof VarDecl) {
				assertDecorations(res.lines, desc, "var");
				return TraversalResult.SKIP_SUBTREE;
			}

			return TraversalResult.CONTINUE;
		});
	}

	private void assertDecorations(List<Decoration> decos, ASTNode node, String... expectedTypes) {
		final Set<String> expectedTypesSet = new HashSet<>(Arrays.asList(expectedTypes));

		// +/- 2 for [[ and ]]
		final int expStart = node.start.getPackedBits() - 2;
		final int expEnd = node.end.getPackedBits() + 2;

		assertTrue(String.format("Available decorations: %s. Wanted: [%d,%d,%s]. Node: %s: %s", //
				decos.stream() //
						.map(x -> String.format("[%d,%d,%s]", x.start, x.end, x.type)) //
						.collect(Collectors.joining(", ")),
				expStart, expEnd, expectedTypesSet, node.loc(), node.pp()),
				decos.stream().anyMatch(x -> x.start == expStart && x.end == expEnd //
						&& (expectedTypesSet.contains(x.type))));

	}

	private void checkQueryCompletion(Query q) {
		final QueryHead head = q.head;
		switch (head.type) {
		case VAR: {
			final String vname = head.asVar().value;
			forEachColumn(q.head, col -> {
				assertItemContains(head, completeAt(head, head.start.line, col), "$" + vname);
			});
			break;
		}
		case TYPE: {
			if (!(head.getParent() instanceof Query)) {
				fail("Unexpected AST structure, TYPE query heads should be inside Queries only");
			}
			forEachColumn(head, col -> {
				final List<CompletionItem> items = completeAt(head, head.start.line, col);

				final String exp = String.format("%s%s", head.asType().value,
						q.index == null ? "" : ("[" + q.index + "]"));
				if (q.index != null && q.index == 0) {
					// It is also OK with a non-suffixed item
					assertItemContains(head, items, exp, head.asType().value);
				} else {
					assertItemContains(head, items, exp);
				}
			});
			break;
		}
		default: {
			fail("Unexpected head type " + head.type);
		}
		}

		final boolean isNormalAssert = q.assertion.isPresent() //
				&& !q.assertion.get().exclamation //
				&& !q.assertion.get().tilde;
		q.traverseDescendants(desc -> {
			if (desc instanceof Query) {
				// Another query
				checkQueryCompletion((Query) desc);
				return TraversalResult.SKIP_SUBTREE;
			}

			if (desc instanceof PropertyAccess) {
				PropertyAccess acc = (PropertyAccess) desc;
				forEachColumn(acc.name, col -> {
					assertItemContains(acc.name, completeAt(acc.name, acc.start.line, col), acc.name.value);
				});
			}

			if (isNormalAssert && desc instanceof ExpectedValue) {

				ExpectedValue cv = (ExpectedValue) desc;
				if (cv.type == Type.CONSTANT) {
					final String cval = cv.asConstant().value;
					forEachColumn(cv, col -> {
						assertItemContains(cv, completeAt(cv, cv.start.line, col), cval);
					});
				}
			}

			return TraversalResult.CONTINUE;
		});

	}

	private void assertItemContains(ASTNode context, List<CompletionItem> actual, String... expected) {
		final Set<String> expSet = new HashSet<>(Arrays.asList(expected));
		final String errMsg = String.format("%s, Expected '%s' to be present, got [%s]", context.loc(),
				expected.length == 1 ? expected[0] : Arrays.asList(expected),
				actual.stream().map(x -> x.insertText).collect(Collectors.joining(", ")));
		assertTrue(errMsg, actual.stream().anyMatch(x -> expSet.contains(x.insertText)));
	}

	private void forEachColumn(ASTNode node, Consumer<Integer> onColumn) {
		for (int col = node.start.column; col <= node.end.column; ++col) {
			onColumn.accept(col);
		}
	}

	private List<CompletionItem> completeAt(ASTNode context, int line, int col) {
		final ClientRequest req = new ClientRequest( //
				new CompleteReq(tc.env.parsingRequestData, line, col).toJSON(), (upd) -> {
				}, new AtomicBoolean(false), p -> {
				});
		final CompleteRes res = CompleteRes.fromJSON(tc.requestHandler.handleRequest(req));
		assertNotNull(String.format("%s: %s", context.loc(), context.pp()), res.lines);
		return res.lines;
	}
}
