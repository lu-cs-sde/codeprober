package addnum.ast;

import java.io.PrintStream;

import addnum.ast.ASTNodeAnnotation.Child;

public class Program extends Node {

	public Program(Node root) {
		super(0, 0);
		addChild(root);
	}

	@Child(name = "rootNode")
	public Node rootNode() {
		return getChild(0);
	}

	@Override
	public int value() {
		return rootNode().value();
	}

	@Override
	public void prettyPrint(PrintStream out) {
		rootNode().prettyPrint(out);
	}

}
