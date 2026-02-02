package addnum.ast;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import addnum.ast.ASTNodeAnnotation.Attribute;

public abstract class Node implements Iterable<Node> {

	private final int start, end;
	protected Node parent;
	private final List<Node> children = new ArrayList<>();

	public Node(int start, int end) {
		this.start = start;
		this.end = end;
	}

	public abstract int value();

	public int getStart() {
		return start;
	}

	public int getEnd() {
		return end;
	}

	public Collection<Node> getChildren() {
		return children;
	}

	public void flushTreeCache() {
		for (Node child : this) {
			child.flushTreeCache();
		}
	}

	public abstract void prettyPrint(PrintStream out);

	@Attribute
	public String prettyPrint() {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final PrintStream ps = new PrintStream(baos);
		prettyPrint(ps);
		return new String(baos.toByteArray());
	}

	public void addChild(Node child) {
		if (child.parent != null) {
			throw new Error("Node is already added to another node");
		}
		child.parent = this;
		children.add(child);
	}

	public Node getParent() {
		return parent;
	}

	public int getNumChild() {
		return children.size();
	}

	public Node getChild(int idx) {
		return children.get(idx);
	}

	public Program program() {
		if (this instanceof Program) {
			return (Program) this;
		}
		return parent.program(); // "Should never crash", as the topmost parent is always a Program node.
	}

	@Override
	public Iterator<Node> iterator() {
		return children.iterator();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	@Override
	public Node clone() throws CloneNotSupportedException {
		return (Node) super.clone();
	}
}
