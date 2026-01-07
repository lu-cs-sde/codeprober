package codeprober.textprobe.ast;

import codeprober.textprobe.ast.ASTNodeAnnotation.Attribute;

public class Label extends AbstractASTNode {

	public final String value;

	public Label(Position start, Position end, String value) {
		super(start, end);
		this.value = value;
	}

	@Attribute
	public String value() {
		return value;
	}

	@Override
	@Attribute
	public String pp() {
		return value;
	}
}
