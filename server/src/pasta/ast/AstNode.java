package pasta.ast;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import pasta.AstInfo;
import pasta.locator.Span;
import pasta.metaprogramming.InvokeProblem;
import pasta.metaprogramming.PositionRepresentation;
import pasta.metaprogramming.Reflect;

public class AstNode {

	private AstNode parent;
	private boolean expandedParent;

	public final Object underlyingAstNode;

	private AstNode[] children;

	private final Set<Object> visitedNodes = new HashSet<>();

	private Span span;

	public AstNode(Object underlyingAstNode) {
		if (underlyingAstNode == null) {
			throw new NullPointerException("Missing underlying node");
		}
		if (underlyingAstNode instanceof AstNode) {
			throw new IllegalArgumentException(
					"Created AstNode with another AstNode ('" + underlyingAstNode + "') as underlying instance");
		}
		this.underlyingAstNode = underlyingAstNode;
	}

	public Boolean pastaVisible() {
		try {
			return (Boolean) Reflect.invoke0(underlyingAstNode, "pastaVisible");
		} catch (InvokeProblem e) {
			// Perfectly fine to not override this, ignore the error
			return null;
		}
	}

	public Span getRawSpan(AstInfo info) throws InvokeProblem {
		return getRawSpan(info.positionRepresentation);
	}

	public Span getRawSpan(PositionRepresentation positionRepresentation) throws InvokeProblem {
		if (this.span == null) {
			switch (positionRepresentation) {
			case PACKED_BITS: {
				this.span = new Span((Integer) Reflect.invoke0(underlyingAstNode, "getStart"),
						(Integer) Reflect.invoke0(underlyingAstNode, "getEnd"));
				break;
			}
			case SEPARATE_LINE_COLUMN: {
				this.span = new Span( //
						((Integer) Reflect.invoke0(underlyingAstNode, "getStartLine") << 12)
								+ ((Number) Reflect.invoke0(underlyingAstNode, "getStartColumn")).intValue(),
						((Integer) Reflect.invoke0(underlyingAstNode, "getEndLine") << 12)
								+ ((Number) Reflect.invoke0(underlyingAstNode, "getEndColumn")).intValue());
				break;
			}
			default: {
				throw new RuntimeException("Unknown position representation");
			}
			}
		}
		return this.span;
	}

	public AstNode parent() {
		if (!expandedParent) {
			Object parent;
			try {
				parent = Reflect.getParent(underlyingAstNode);
			} catch (InvokeProblem e) {
				System.out.println("Invalid AST node (invalid parent): " + underlyingAstNode);
				e.printStackTrace();
				throw new AstParentException();
			}
			if (parent == null) {
				this.parent = null;
			} else {
				if (!visitedNodes.add(parent)) {
					System.out.println("Cycle detected in AST graph detected");
					throw new AstLoopException();
				}
				this.parent = new AstNode(parent);
				this.parent.visitedNodes.addAll(this.visitedNodes);
			}
			expandedParent = true;
		}
		return parent;
	}

	public int getNumChildren() {
		if (children == null) {
			int numCh = (Integer) Reflect.invoke0(underlyingAstNode, "getNumChild");
			this.children = new AstNode[numCh];
		}
		return this.children.length;
	}

	public AstNode getNthChild(int n) {
		final int len = getNumChildren();
		if (n < 0 || n >= len) {
			throw new ArrayIndexOutOfBoundsException(
					"This node has " + len + " " + (len == 1 ? "child" : "children") + ", index " + n + " is invalid");
		}
		if (children[n] == null) {
			children[n] = new AstNode(Reflect.invokeN(underlyingAstNode, "getChild", new Class<?>[] { Integer.TYPE },
					new Object[] { n }));
			children[n].parent = this;
			children[n].expandedParent = true;
			children[n].visitedNodes.addAll(this.visitedNodes);
		}
		return children[n];
	}

	public Iterable<AstNode> getChildren() {
		final int len = getNumChildren();
		return new Iterable<AstNode>() {

			@Override
			public Iterator<AstNode> iterator() {

				return new Iterator<AstNode>() {
					int pos;

					@Override
					public boolean hasNext() {
						return pos < len;
					}

					@Override
					public AstNode next() {
						return getNthChild(pos++);
					}
				};
			}
		};
	}

	@Override
	public String toString() {
		return "AstNode<" + underlyingAstNode + ">";
	}

	public boolean isList() {
		return underlyingAstNode.getClass().getSimpleName().equals("List");
	}

	public boolean isOpt() {
		return underlyingAstNode.getClass().getSimpleName().equals("Opt");
	}

	public boolean sharesUnderlyingNode(AstNode other) {
		return underlyingAstNode == other.underlyingAstNode;
	}
}
