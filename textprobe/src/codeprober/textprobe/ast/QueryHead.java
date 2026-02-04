package codeprober.textprobe.ast;

public class QueryHead extends AbstractASTNode {

	public static enum Type {
		TYPE, VAR,
	}

	public final Type type;
	private final ASTNode value;

	private QueryHead(Type type, ASTNode value) {
		super(value.start, value.end);
		this.type = type;
		this.value = addChild(value);
	}

	public static QueryHead fromType(TypeQueryHead type) {
		return new QueryHead(Type.TYPE, type);
	}

	public static QueryHead fromVar(VarUse varName) {
		return new QueryHead(Type.VAR, varName);
	}

	public TypeQueryHead asType() {
		return (TypeQueryHead) value;
	}

	public VarUse asVar() {
		return (VarUse) value;
	}

	@Override
	public String pp() {
		return value.pp();
	}
}
