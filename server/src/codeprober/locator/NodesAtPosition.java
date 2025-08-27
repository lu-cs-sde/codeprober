package codeprober.locator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.data.NodeLocator;

public class NodesAtPosition {

	public static List<NodeLocator> get(AstInfo info, AstNode astNode, int pos) {
		List<NodeLocator> ret = new ArrayList<>();
		getTo(ret, info, astNode, pos);
		Collections.reverse(ret); // Narrowest/smallest node first in the list
		return ret;
	}

	private static void getTo(List<NodeLocator> out, AstInfo info, AstNode astNode, int pos) {
		final Span nodePos;
		try {
			nodePos = astNode.getRecoveredSpan(info);
		} catch (InvokeProblem e1) {
			e1.printStackTrace();
			return;
		}
		final Boolean cutoff = astNode.cutoffNodeListTree(info);
		if (cutoff != null && cutoff) {
			return;
		}
		boolean includeNode = out.isEmpty()
				|| (nodePos.start == 0 || nodePos.end == 0 || (nodePos.start <= pos && (nodePos.end + 1) >= pos));
		if (includeNode) {

			// Default false for List/Opt, they are very rarely useful
			boolean show = !astNode.isList() && !astNode.isOpt();

			final Boolean override = astNode.showInNodeList(info);
			if (override != null ? override : show) {
				out.add(CreateLocator.fromNode(info, astNode));
			}
		}
		if (astNode == info.ast) {
			// Root node, maybe skip ahead
			Object next = null;
			try {
				next = Reflect.invoke0(astNode.underlyingAstNode, "pastaVisibleNextAfterRoot");
			} catch (InvokeProblem e) {
				// OK, this is an optional attribute
			}
			if (next != null) {
				getTo(out, info, new AstNode(next), pos);
				return;
			}
		}
		if (includeNode) {
			for (AstNode child : astNode.getChildren(info)) {
				getTo(out, info, child, pos);
			}
		}
	}
}
