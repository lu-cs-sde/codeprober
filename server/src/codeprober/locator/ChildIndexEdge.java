package codeprober.locator;

import codeprober.ast.AstNode;
import codeprober.locator.NodeEdge.NodeEdgeType;

public class ChildIndexEdge extends NodeEdge {
	public ChildIndexEdge(AstNode sourceNode, TypeAtLoc sourceLoc, AstNode targetNode, TypeAtLoc targetLoc,
			int childIndex) {
		super(sourceNode, sourceLoc, targetNode, targetLoc, NodeEdgeType.ChildIndex, childIndex);
	}
}