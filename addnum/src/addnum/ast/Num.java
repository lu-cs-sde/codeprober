package addnum.ast;

import java.io.PrintStream;
import java.util.List;

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

	public String cpr_ppPrefix() {
		return val + "";
	}

	private Node asAdd_value = null;

	@Attribute(isNTA = true)
	public Node asAdd() {
		if (asAdd_value == null) {
			asAdd_value = new Add(new Num(0, 0, 0), new Num(0, 0, val));
		}
		asAdd_value.parent = this;
		return asAdd_value;
	}

	public List<String> cpr_extraAstReferences() {
		if (value() == 42) {
			return java.util.Arrays.asList("asAdd", "program");
		}
		return null;
	}
}
