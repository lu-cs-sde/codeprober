package addnum.ast;

import java.io.PrintStream;

import addnum.ast.ASTNodeAnnotation.Attribute;
import addnum.ast.ASTNodeAnnotation.Child;

public class Group extends Node {

	public Group(int start, int end, Node node) {
		super(start, end);
		addChild(node);
	}

	@Child(name = "node")
	public Node node() {
		return getChild(0);
	}

	@Attribute
	public int value() {
		return node().value();
	}

	@Override
	public void prettyPrint(PrintStream out) {
		out.print("(");
		node().prettyPrint(out);
		out.print(")");
	}
}
