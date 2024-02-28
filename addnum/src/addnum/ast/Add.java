package addnum.ast;

import java.io.PrintStream;

import addnum.ast.ASTNodeAnnotation.Attribute;
import addnum.ast.ASTNodeAnnotation.Child;

public class Add extends Node {

	public Add(Node lhs, Node rhs) {
		super(lhs.getStart(), rhs.getEnd());
		addChild(lhs);
		addChild(rhs);
	}

	@Child(name="lhs")
	public Node lhs() {
		return getChild(0);
	}

	@Child(name="rhs")
	public Node rhs() {
		return getChild(1);
	}

	@Attribute
	public int value() {
		return lhs().value() + rhs().value();
	}

	private Node asNum_value = null;
	@Attribute
	public Node asNum() {
		if (asNum_value == null) {
			asNum_value = new Num(0, 0, value());
		}
		asNum_value.parent = this;
		return asNum_value;
	}

	@Override
	public void prettyPrint(PrintStream out) {
		out.append("(");
		lhs().prettyPrint(out);
		out.append(" + ");
		rhs().prettyPrint(out);
		out.append(")");
	}
}
