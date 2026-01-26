package addnum.ast;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import addnum.FluentTestHelper;
import addnum.ast.ASTNodeAnnotation.Attribute;
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

	@Attribute
	public List<Diagnostic> highlight(int needle) {
		final List<Diagnostic> ret = new ArrayList<>();

		final List<Node> toVisit = new ArrayList<>();
		toVisit.add(this);

		while (!toVisit.isEmpty()) {
			final Node last = toVisit.remove(toVisit.size() - 1);
			if (last.value() == needle) {
				ret.add(new Diagnostic(last,
						String.format("INFO@%d;%d;value=%d", last.getStart(), last.getEnd(), needle)));
			}
			for (Node child : last) {
				toVisit.add(child);
			}
		}

		return ret;
	}

	public List<String> cpr_extraAstReferences() {
		return java.util.Arrays.asList("leftmostChild");
	}

	public Object leftmostChild() {
		Node child = this;
		while (child.getNumChild() > 0) {
			child = child.getChild(0);
		}
		return child;
	}

	@Attribute
	public Object greet(Object obj) {
		return "Hello " + obj;
	}

	@Attribute
	public FluentTestHelper fluentTest() {
		return new FluentTestHelper();
	}
}
