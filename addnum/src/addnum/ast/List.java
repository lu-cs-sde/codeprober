package addnum.ast;

import java.io.PrintStream;

public class List extends Node {

	public List(Node... nodes) {
		super(nodes.length == 0 ? 0 : nodes[0].getStart(), nodes.length == 0 ? 0 : nodes[0].getEnd());
		for (Node n : nodes) {
			addChild(n);
		}
	}

	public List(java.util.List<Node> nodes) {
		this(nodes.toArray(new Node[nodes.size()]));
	}

	@Override
	public void prettyPrint(PrintStream out) {
		out.append("[");
		boolean first = true;
		for (Node child : this) {
			if (first) {
				first = false;
			} else {
				out.append(", ");
			}
			child.prettyPrint(out);
		}
		out.append("]");
	}

	public String cpr_ppPrefix() {
		return "[";
	}

	public String cpr_ppInfix(int idx) {
		return ", ";
	}

	public String cpr_ppSuffix() {
		return "]";
	}

	@Override
	public int value() {
		return -1;
	}
}
