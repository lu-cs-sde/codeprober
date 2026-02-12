package codeprober.textprobe.ast;

import codeprober.textprobe.ast.ASTNodeAnnotation.Child;

public class Expr extends AbstractASTNode {

	public static enum Type {
		INT, STRING, QUERY,
	}

	public final Type type;
	private final Object value;

	private Expr(Type type, Position start, Position end, Object value) {
		super(start, end);
		this.type = type;
		this.value = value;
	}

	private Expr(Type type, ASTNode value) {
		super(value.start, value.end);
		this.type = type;
		this.value = addChild(value);
	}

	public static Expr fromInt(Position start, Position end, Integer value) {
		return new Expr(Type.INT, start, end, value);
	}

	public static Expr fromString(Position start, Position end, String value) {
		return new Expr(Type.STRING, start, end, value);
	}

	public static Expr fromQuery(Query value) {
		return new Expr(Type.QUERY, value);
	}

	@Child(name = "type")
	public Type type() {
		return type;
	}

	public Integer asInt() {
		return (Integer) value;
	}

	public String asString() {
		return (String) value;
	}

	public Query asQuery() {
		return (Query) value;
	}

	@Override
	public String pp() {
		switch (type) {
		case INT:
			return String.valueOf(asInt());
		case STRING:
			return "\"" + asString() + "\"";
		case QUERY:
			return asQuery().pp();
		default:
			return "?";
		}
	}
}
