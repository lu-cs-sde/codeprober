package codeprober.textprobe.ast;

import java.util.function.BiConsumer;

import codeprober.textprobe.ast.ASTNodeAnnotation.Attribute;

public class VarUse extends Label {

	public VarUse(Position start, Position end, String value) {
		super(start, end, value);
	}

	@Override
	@Attribute
	public String pp() {
		return "$" + super.pp();
	}

	@Attribute
	public VarDecl decl() {
		return doc().varDecls().get(value);
	}

	@Override
	protected void doCollectProblems(BiConsumer<ASTNode, String> addErr) {
		if (doc().varDecls().get(value) == null) {
			addErr.accept(this, String.format("No such var '%s'", value));
		}
	}

}
