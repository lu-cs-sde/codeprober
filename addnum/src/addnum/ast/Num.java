package addnum.ast;

import java.io.PrintStream;
import java.util.ArrayList;

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

	@Override
	public void flushTreeCache() {
		asAdd_value = null;
		super.flushTreeCache();
	}

	private Node asAdd_value = null;

	@Attribute(isNTA = true)
	public Node asAdd() {
		if (asAdd_value == null) {
			asAdd_value = new Add(new Num(0, 0, 0), new Num(0, 0, val));
			asAdd_value.parent = this;
		}
		return asAdd_value;
	}

	public java.util.List<String> cpr_extraAstReferences() {
		if (value() == 42) {
			return java.util.Arrays.asList("asAdd", "program");
		}
		return null;
	}

	private Node asListOfOnes_value = null;

	@Attribute(isNTA = true)
	public Node asListOfOnes() {
		if (asListOfOnes_value == null) {
			java.util.List<Node> nodes = new ArrayList<>();
			final int val = value();
			for (int i = 0; i < val; ++i) {
				nodes.add(new Num(getStart(), getEnd(), 1));
			}
			asListOfOnes_value = new List(nodes);
			asListOfOnes_value.parent = this;
		}
		return asListOfOnes_value;
	}

	@Attribute
	public String digitsOnMultipleLines() {
		return String.join("\n", String.valueOf(val).split(""));
	}

	@Attribute
	public boolean matchesAny(Integer... vals) {
		for (Integer i : vals) {
			if (i.equals(val)) {
				return true;
			}
		}
		return false;
	}

	@Attribute
	public String join(String separator, Object... parts) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.length; ++i) {
			if (i > 0) {
				sb.append(separator);
			}
			sb.append(parts[i]);
		}
		return sb.toString();
	}

	@Override
	public Num clone() {
		return new Num(getStart(), getEnd(), val);
	}

	@Attribute
	public boolean isEven() {
		return val % 2 == 0;
	}
}
