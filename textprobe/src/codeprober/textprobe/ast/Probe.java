package codeprober.textprobe.ast;

import codeprober.textprobe.ast.ASTNodeAnnotation.Attribute;

// A generic text probe representation. Represents data within "[[" and "]]"
public class Probe extends AbstractASTNode {

	public static enum Type {
		QUERY, VARDECL
	}

	public final Type type;
	private final ASTNode value;

	private Probe(Type type, ASTNode value) {
		super(value.start, value.end);
		this.type = type;
		this.value = addChild(value);
	}

	@Attribute
	public Type type() {
		return type;
	}

	public static Probe fromQuery(Query query) {
		return new Probe(Type.QUERY, query);
	}

	public static Probe fromVarDecl(VarDecl dec) {
		return new Probe(Type.VARDECL, dec);
	}

	public Query asQuery() {
		return (Query) value;
	}

	public VarDecl asVarDecl() {
		return (VarDecl) value;
	}

	@Override
	public String pp() {
		return value.pp();
	}
}
