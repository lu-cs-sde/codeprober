package codeprober.locator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.data.NodeContainer;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.RpcBodyLine;

public class PrettyPrintTree {

	private static String extractPPStr(AstInfo info, AstNode node, String funcName) {
		if (info.hasOverride0(node.underlyingAstNode.getClass(), funcName)) {
			try {
				return (String) Reflect.invoke0(node.underlyingAstNode, funcName);
			} catch (ClassCastException | InvokeProblem e) {
				System.err.println("Error invoing " + funcName);
				e.printStackTrace();
			}
		}
		return null;
	}

	private static RpcBodyLine prettyPrint(AstInfo info, AstNode node, AtomicBoolean didEncounterAnyCustomString) {
		final NodeLocator loc = CreateLocator.fromNode(info, node);
		if (loc == null) {
			return null;
		}
		if (node.getNumChildren(info) == 0) {
		}
		List<RpcBodyLine> children = new ArrayList<>();
		final String prefix = extractPPStr(info, node, "cpr_ppPrefix");
		if (prefix != null) {
			didEncounterAnyCustomString.set(true);
			children.add(RpcBodyLine.fromPlain(prefix));
		}
		boolean hasInfix = info.hasOverride1(node.underlyingAstNode.getClass(), "cpr_ppInfix", Integer.TYPE);
		int childIdx = -1;
		for (AstNode child : node.getChildren(info)) {
			if (hasInfix && childIdx >= 0) {
				try {
					final String infix = (String) Reflect.invokeN(node.underlyingAstNode, "cpr_ppInfix",
							new Class[] { Integer.TYPE }, new Object[] { childIdx });
					if (infix != null) {
						didEncounterAnyCustomString.set(true);
						children.add(RpcBodyLine.fromPlain(infix));
					}
				} catch (ClassCastException | InvokeProblem e) {
					System.err.println("Error invoing cpr_ppInfix(" + childIdx + ")");
					e.printStackTrace();
				}

			}
			children.add(prettyPrint(info, child, didEncounterAnyCustomString));
			++childIdx;
		}
		final String suffix = extractPPStr(info, node, "cpr_ppSuffix");
		if (suffix != null) {
			didEncounterAnyCustomString.set(true);
			children.add(RpcBodyLine.fromPlain(suffix));
		}
		if (children.isEmpty()) {
			return RpcBodyLine.fromNodeContainer(new NodeContainer(loc, null));
		}
		return RpcBodyLine.fromNodeContainer(new NodeContainer(loc, RpcBodyLine.fromArr(children)));
	}

	public static RpcBodyLine prettyPrint(AstInfo info, AstNode node) {
		final AtomicBoolean didEncounterAnyCustomString = new AtomicBoolean();
		RpcBodyLine ret = prettyPrint(info, node, didEncounterAnyCustomString);
		if (didEncounterAnyCustomString.get()) {
			// OK!
			return ret;
		}
		return RpcBodyLine.fromArr(Arrays.asList(ret, //
				RpcBodyLine.fromStdout(
						"No pretty-print function detected. Please implement one or more of the following to make use of the pretty-print feature:"), //
				RpcBodyLine.fromStdout("  public String cpr_ppPrefix() { ... }"), //
				RpcBodyLine.fromStdout("  public String cpr_ppInfix(int) { ... }"), //
				RpcBodyLine.fromStdout("  public String cpr_ppSuffix() { ... }") //

		));
	}
}
