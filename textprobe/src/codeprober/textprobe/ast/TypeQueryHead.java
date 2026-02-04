package codeprober.textprobe.ast;

import java.util.Set;
import java.util.function.BiConsumer;

import codeprober.textprobe.ast.ASTNodeAnnotation.Attribute;

public class TypeQueryHead extends AbstractASTNode {

	public final boolean bumpUp;
	public final Label label;

	public TypeQueryHead(Position start, boolean bumpUp, Label label) {
		super(start, label.end);
		this.bumpUp = bumpUp;
		this.label = addChild(label);
	}

	@Attribute
	public boolean bumpUp() {
		return bumpUp;
	}

	@Attribute
	public Label label() {
		return label;
	}

	@Override
	public String pp() {
		return String.format("%s%s", bumpUp ? "^" : "", label.pp());
	}

	@Attribute
	public int bumpedLine() {
		if (!bumpUp) {
			return start.line;
		}
		final Set<Integer> bumpedLines = doc().bumpedLines();
		int line = start.line;
		while (line > 1 && bumpedLines.contains(line)) {
			--line;
		}
		return line;
	}

	@Override
	protected void doCollectProblems(BiConsumer<ASTNode, String> addErr) {
		if (!bumpUp && doc().bumpedLines().contains(start.line)) {
			addErr.accept(this, "Cannot mix '^' and non-'^' queries on the same line");
		} else if (bumpUp && start.line <= 1) {
			addErr.accept(this, "Cannot use '^' on the first line");
		}
	}
}
