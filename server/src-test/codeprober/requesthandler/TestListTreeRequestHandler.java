package codeprober.requesthandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.protocol.data.ListTreeReq;
import codeprober.protocol.data.ListTreeRes;
import codeprober.protocol.data.ListedTreeNode;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.NodeLocatorStep;
import codeprober.protocol.data.TALStep;

public class TestListTreeRequestHandler {

	@Test
	public void testListDownFromRoot() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getSimple()));

		final NodeLocator rootLocator = new NodeLocator(new TALStep("", null, 0, 0, 0, null), Collections.emptyList());
		final ListTreeRes result = ListTreeRequestHandler.apply(new ListTreeReq("ListTreeDownwards", rootLocator, null),
				TestEvaluatePropertyHandler.createHardcodedParser(info));

		assertNotNull(result);
		assertEquals(0, result.body.size());
		assertNotNull(result.locator);
		assertEquals("Program[Foo[Bar], Baz]", stringifyListedNode(result.node));
	}

	private static String stringifyListedNode(ListedTreeNode ltn) {
		final StringBuilder sb = new StringBuilder();
		if (ltn.name != null) {
			sb.append(ltn.name + ":");
		}
		sb.append(ltn.locator.result.type.substring(ltn.locator.result.type.lastIndexOf('$') + 1));

		switch (ltn.children.type) {
		case children: {
			final List<ListedTreeNode> children = ltn.children.asChildren();
			if (children.size() != 0) {
				sb.append("[");
				for (int i = 0; i < children.size(); i++) {
					if (i > 0) {
						sb.append(", ");
					}
					sb.append(stringifyListedNode(children.get(i)));
				}
				sb.append("]");
			}
			break;
		}
		case placeholder: {
			sb.append("[.." + ltn.children.asPlaceholder() + "..]");
			break;
		}
		}
		return sb.toString();
	}

	@Test
	public void testBadLocator() {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getSimple()));

		// Child step 123 is invalid, applying it to 'info' should result in a failure.
		// This test ensures that ListTreeRequestHandler handles bad locators
		final NodeLocator bogusLocator = new NodeLocator(new TALStep("NonExisting", null, 0, 0, 0, null),
				Arrays.asList(NodeLocatorStep.fromChild(123)));
		final ListTreeRes result = ListTreeRequestHandler.apply(
				new ListTreeReq("ListTreeDownwards", bogusLocator, null),
				TestEvaluatePropertyHandler.createHardcodedParser(info));

		assertNotNull(result);
		assertEquals(0, result.body.size());
		assertEquals(null, result.locator);
		assertEquals(null, result.node);
	}

}
