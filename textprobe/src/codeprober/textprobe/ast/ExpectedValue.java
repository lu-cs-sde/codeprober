package codeprober.textprobe.ast;

import codeprober.textprobe.ast.ASTNodeAnnotation.Attribute;
import codeprober.textprobe.ast.ASTNodeAnnotation.Child;

public class ExpectedValue extends AbstractASTNode {

	public static enum Type {
		CONSTANT, QUERY
	}

	public final Type type;
	private final ASTNode value;

	private ExpectedValue(Type type, ASTNode value) {
		super(value.start, value.end);
		this.type = type;
		this.value = addChild(value);
	}

	public static ExpectedValue fromConstant(Label val) {
		return new ExpectedValue(Type.CONSTANT, val);
	}

	public static ExpectedValue fromQuery(Query query) {
		return new ExpectedValue(Type.QUERY, query);
	}

	@Attribute
	@Child(name = "type")
	public Type type() {
		return type;
	}

	public Label asConstant() {
		return (Label) value;
	}

	public Query asQuery() {
		return (Query) value;
	}

	@Override
	public String pp() {
		switch (type) {
		case CONSTANT:
			return String.format("\"%s\"", value.pp());
		default:
			return value.pp();
		}
	}
}
