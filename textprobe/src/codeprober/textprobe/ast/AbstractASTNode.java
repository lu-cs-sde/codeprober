package codeprober.textprobe.ast;

import java.util.List;
import java.util.ArrayList;

public abstract class AbstractASTNode extends ASTNode {

	protected final List<ASTNode> children = new ArrayList<>();

	public AbstractASTNode(Position start, Position end) {
		super(start, end);
	}

	public AbstractASTNode() {
		super();
	}

	@Override
	public int getNumChild() {
		return children.size();
	}

	@Override
	public ASTNode getChild(int idx) {
		return children.get(idx);
	}

	protected <S extends ASTNode> S addChild(S child) {
		children.add(adopt(child));
		return child;
	}
}
