package addnum.ast;

import java.io.PrintStream;

import addnum.ast.ASTNodeAnnotation.Attribute;

public class Num extends Node {

	private final int val;

	public Num(int start, int end, int val) {
		super(start, end);
		this.val = val;
	}

	@Attribute
	public int value() {
		return val;
	}

	@Override
	public void prettyPrint(PrintStream out) {
		out.append("" + val);
	}
}
