package codeprober.textprobe.ast;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import codeprober.textprobe.ast.ASTNodeAnnotation.Attribute;

public class ASTNode {
	protected ASTNode parent;
	public final Position start;
	public final Position end;

	public ASTNode(Position start, Position end) {
		this.start = start != null ? start : Position.NIL;
		this.end = end != null ? end : Position.NIL;
	}

	public ASTNode() {
		this(null, null);
	}

	public int cpr_getStartLine() {
		return start.line;
	}

	public int cpr_getStartColumn() {
		return start.column;
	}

	public int cpr_getEndLine() {
		return end.line;
	}

	public int cpr_getEndColumn() {
		return end.column;
	}

	public int getNumChild() {
		return 0;
	}

	public ASTNode getParent() {
		return parent;
	}

	public ASTNode getChild(int idx) {
		return null;
	}

	@Attribute
	public Document doc() {
		return parent.doc();
	}

	@Attribute
	public Container enclosingContainer() {
		return parent == null ? null : parent.enclosingContainer();
	}

	public <T extends ASTNode> T adopt(T other) {
		other.parent = this;
		return other;
	}

	@Attribute
	public String pp() {
		return "";
	}

	@Attribute
	public String loc() {
		return String.format("[%s->%s]", start, end);
	}

	public final void collectProblems(BiConsumer<ASTNode, String> addErr) {
		doCollectProblems(addErr);
		for (int i = 0; i < getNumChild(); ++i) {
			getChild(i).collectProblems(addErr);
		}
	}

	protected void doCollectProblems(BiConsumer<ASTNode, String> addErr) {
		// Empty by default
	}

	public void forEachDescendant(Consumer<ASTNode> callback) {
		for (int i = 0; i < getNumChild(); ++i) {
			final ASTNode child = getChild(i);
			callback.accept(child);
			child.forEachDescendant(callback);
		}
	}

	public void traverseDescendants(Function<ASTNode, TraversalResult> callback) {
		for (int i = 0; i < getNumChild(); ++i) {
			final ASTNode child = getChild(i);
			final TraversalResult res = callback.apply(child);
			if (res == TraversalResult.SKIP_SUBTREE) {
				return;
			}
			child.traverseDescendants(callback);
		}
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj;
	}

	public static enum TraversalResult {
		CONTINUE, SKIP_SUBTREE;
	}
}
