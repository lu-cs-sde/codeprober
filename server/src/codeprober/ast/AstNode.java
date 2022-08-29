package codeprober.ast;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import codeprober.AstInfo;
import codeprober.locator.Span;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.PositionRepresentation;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.PositionRecoveryStrategy;

public class AstNode {

	private AstNode parent;
	private boolean expandedParent;

	public final Object underlyingAstNode;

	private AstNode[] children;

	private final Set<Object> visitedNodes = new HashSet<>();

	private Span rawSpan;
	
	private PositionRecoveryStrategy recoveredSpanStrategy;
	private Span recoveredSpan;

	private Boolean isNonOverlappingSibling = null;
	
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

	public boolean isLocatorTALRoot(AstInfo info) {
		return underlyingAstNode.getClass() == info.getLocatorTALRoot();
	}
	
	public Boolean showInNodeList(AstInfo info) {
		if (!info.hasOverride0(underlyingAstNode.getClass(), "cpr_nodeListVisible")) {
			return null;
		}
		try {
			return (Boolean) Reflect.invoke0(underlyingAstNode, "cpr_nodeListVisible");
		} catch (InvokeProblem e) {
			// Perfectly fine to not override this, ignore the error
			return null;
		}
	}
	
	public Boolean cutoffNodeListTree(AstInfo info) {
		if (!info.hasOverride0(underlyingAstNode.getClass(), "cpr_cutoffNodeListTree")) {
			return null;
		}
//		try {
			return (Boolean) Reflect.invoke0(underlyingAstNode, "cpr_cutoffNodeListTree");
//		} catch (InvokeProblem e) {
//			// Perfectly fine to not override this, ignore the error
//			return null;
//		}

	}

	public boolean isNonOverlappingSibling(AstInfo info) {
		if (isNonOverlappingSibling != null) {
			return isNonOverlappingSibling;
		}
		
		if (parent == null) {
			isNonOverlappingSibling = false;
		} else {
			Span our = getRecoveredSpan(info);
//			if (!our.isMeaningful()) {
//				isNonOverlappingSibling = false;
//				return false;
//			}
			boolean foundSelf = false;
			for (AstNode sibling : parent.getChildren()) {
				if (sibling.underlyingAstNode == this.underlyingAstNode) {
					foundSelf = true;
					continue;
				}
				Span their = sibling.getRecoveredSpan(info);
				if (their.overlaps(our)) {
					isNonOverlappingSibling = false;
					return false;
				}
			}
			isNonOverlappingSibling = foundSelf;
		}
		return isNonOverlappingSibling;
	}
	public Span getRawSpan(AstInfo info) throws InvokeProblem {
		return getRawSpan(info.positionRepresentation);
	}

	public Span getRawSpan(PositionRepresentation positionRepresentation) throws InvokeProblem {
		if (this.rawSpan == null) {
			switch (positionRepresentation) {
			case PACKED_BITS: {
				this.rawSpan = new Span((Integer) Reflect.invoke0(underlyingAstNode, "getStart"),
						(Integer) Reflect.invoke0(underlyingAstNode, "getEnd"));
				break;
			}
			case SEPARATE_LINE_COLUMN: {
				this.rawSpan = new Span( //
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
		return this.rawSpan;
	}

	public static Span extractPositionDownwards(AstInfo info, AstNode astNode) {
		final Span ownPos = astNode.getRawSpan(info);
		if (!ownPos.isMeaningful()) {
			final AstNode child = astNode.getNumChildren() > 0 ? astNode.getNthChild(0) : null;
			if (child != null) {
				return extractPositionDownwards(info, child);
			}
		}
		return ownPos;
	}

	private static Span extractPositionUpwards(AstInfo info, AstNode astNode) {
		final Span ownPos = astNode.getRawSpan(info);
		if (!ownPos.isMeaningful()) {
			final AstNode parent = astNode.parent();
			if (parent != null) {
				return extractPositionUpwards(info, parent);
			}
		}
		return ownPos;
	}
	
	private Span setAndGetRecoveredSpan(AstInfo info, Span recoveredSpan) {
		this.recoveredSpanStrategy = info.recoveryStrategy;
		this.recoveredSpan = recoveredSpan;
		return this.recoveredSpan;
	}
	
	public Span getRecoveredSpan(AstInfo info) {
		if (this.recoveredSpan != null && this.recoveredSpanStrategy == info.recoveryStrategy) {
			return this.recoveredSpan;
		}
		final Span ownPos = getRawSpan(info);
		if (ownPos.isMeaningful()) {
			return setAndGetRecoveredSpan(info, ownPos);
		}
		switch (info.recoveryStrategy) {
		case FAIL:
			return ownPos;
		case PARENT:
			return setAndGetRecoveredSpan(info, extractPositionUpwards(info, this));
		case CHILD:
			return setAndGetRecoveredSpan(info, extractPositionDownwards(info, this));
		case SEQUENCE_PARENT_CHILD: {
			final Span parent = extractPositionUpwards(info, this);
			return setAndGetRecoveredSpan(info, parent.isMeaningful() ? parent : extractPositionDownwards(info, this));
		}
		case SEQUENCE_CHILD_PARENT: {
			final Span parent = extractPositionDownwards(info, this);
			return setAndGetRecoveredSpan(info, parent.isMeaningful() ? parent : extractPositionUpwards(info, this));
		}
		case ALTERNATE_PARENT_CHILD: {
			AstNode parent = this;
			AstNode child = parent;
			while (parent != null || child != null) {
				if (parent != null) {
					parent = parent.parent();
					if (parent != null) {
						final Span parPos = parent.getRawSpan(info);
						if (parPos.isMeaningful()) {
							return setAndGetRecoveredSpan(info, parPos);
						}
					}
				}
				if (child != null) {
					child = child.getNumChildren() > 0 ? child.getNthChild(0) : null;
					if (child != null) {
						final Span childPos = child.getRawSpan(info);
						if (childPos.isMeaningful()) {
							return setAndGetRecoveredSpan(info, childPos);
						}
					}
				}
			}
			return ownPos;
		}
		default: {
			System.err.println("Unknown recovery strategy " + info.recoveryStrategy);
			return ownPos;
		}
		}
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
