package pasta.locator;

import pasta.ast.AstNode;
import pasta.locator.NodeEdge.NodeEdgeType;

public class ChildIndexEdge extends NodeEdge {
	public ChildIndexEdge(AstNode sourceNode, TypeAtLoc sourceLoc, AstNode targetNode, TypeAtLoc targetLoc,
			int childIndex) {
		super(sourceNode, sourceLoc, targetNode, targetLoc, NodeEdgeType.ChildIndex, childIndex);
	}
}