package codeprober.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import codeprober.AstInfo;
import codeprober.locator.Span;
import codeprober.metaprogramming.AstNodeApiStyle;
import codeprober.metaprogramming.InvokeProblem;
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

	private Boolean shouldBeVisibleInAstView;

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

		if (info.hasOverride0(underlyingAstNode.getClass(), "cpr_isTALRoot")) {
			final Object res = Reflect.invoke0(underlyingAstNode, "cpr_isTALRoot");
			if (res instanceof Boolean) {
				return ((Boolean) res).booleanValue();
			}
			System.out.println("Got non-boolean from cpr_isTALRoot: " + res);
			return false;
		}

		// TODO remove support for this after some time
		if (underlyingAstNode.getClass() == info.getLocatorTALRoot()) {
			System.out.println("WARN: Using deprecated property 'cpr_locatorTALRoot' on root node");
			System.out.println("    | Please implement 'boolean cpr_isTALRoot' on the node of interest instead.");
			System.out.println(
					"    | For example, if you have 'String Program.cpr_locatorTALRoot() = \"CompilationUnit\";'");
			System.out.println("    |         ..then change to 'boolean CompilationUnit.cpr_isTALRoot() = true;'");
			return true;
		}

		return false;
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

	public Boolean showInPropertySearchProbe(AstInfo info, String propName) {
		if (info.hasOverride0(underlyingAstNode.getClass(), "cpr_propSearchVisible")) {
			try {
				return (Boolean) Reflect.invoke0(underlyingAstNode, "cpr_propSearchVisible");
			} catch (InvokeProblem e) {
				// Perfectly fine to not override this, ignore the error
			}
		}
		if (info.hasOverride1(underlyingAstNode.getClass(), "cpr_propSearchVisible", String.class)) {
			try {
				return (Boolean) Reflect.invokeN(underlyingAstNode, "cpr_propSearchVisible",
						new Class[] { String.class }, new Object[] { propName });
			} catch (InvokeProblem e) {
				// Perfectly fine to not override this, ignore the error
			}
		}
		return null;
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

		if (parent() == null) {
			isNonOverlappingSibling = false;
		} else {
			Span our = getRecoveredSpan(info);
//			if (!our.isMeaningful()) {
//				isNonOverlappingSibling = false;
//				return false;
//			}
			boolean foundSelf = false;
			for (AstNode sibling : parent.getChildren(info)) {
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
		return getRawSpan(info.astApiStyle);
	}

	public Span getRawSpan(AstNodeApiStyle positionRepresentation) throws InvokeProblem {
		if (this.rawSpan == null) {
			switch (positionRepresentation) {

			case CPR_EVERYTHING: // Fall through
			case CPR_SEPARATE_LINE_COLUMN: {
				this.rawSpan = new Span( //
						((Integer) Reflect.invoke0(underlyingAstNode, "cpr_getStartLine") << 12)
								+ ((Number) Reflect.invoke0(underlyingAstNode, "cpr_getStartColumn")).intValue(),
						((Integer) Reflect.invoke0(underlyingAstNode, "cpr_getEndLine") << 12)
								+ ((Number) Reflect.invoke0(underlyingAstNode, "cpr_getEndColumn")).intValue());
				break;
			}
			case BEAVER_PACKED_BITS: {
				this.rawSpan = new Span((Integer) Reflect.invoke0(underlyingAstNode, "getStart"),
						(Integer) Reflect.invoke0(underlyingAstNode, "getEnd"));
				break;
			}
			case JASTADD_SEPARATE_LINE_COLUMN: {
				this.rawSpan = new Span( //
						((Integer) Reflect.invoke0(underlyingAstNode, "getStartLine") << 12)
								+ ((Number) Reflect.invoke0(underlyingAstNode, "getStartColumn")).intValue(),
						((Integer) Reflect.invoke0(underlyingAstNode, "getEndLine") << 12)
								+ ((Number) Reflect.invoke0(underlyingAstNode, "getEndColumn")).intValue());
				break;
			}
			case PMD_SEPARATE_LINE_COLUMN: {
				this.rawSpan = new Span( //
						((Integer) Reflect.invoke0(underlyingAstNode, "getBeginLine") << 12)
								+ ((Number) Reflect.invoke0(underlyingAstNode, "getBeginColumn")).intValue(),
						((Integer) Reflect.invoke0(underlyingAstNode, "getEndLine") << 12)
								+ ((Number) Reflect.invoke0(underlyingAstNode, "getEndColumn")).intValue());
				break;
			}
			case JASTADD_NO_POSITION: {
				this.rawSpan = new Span(0, 0);
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
			final AstNode child = astNode.getNumChildren(info) > 0 ? astNode.getNthChild(info, 0) : null;
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
					child = child.getNumChildren(info) > 0 ? child.getNthChild(info, 0) : null;
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
//				this.parent.registerChild(info, this);
			}
			expandedParent = true;
		}
		return parent;
	}

//	private void registerChild(AstInfo info, AstNode potentialChild) {
//		final int num = getNumChildren(info);
//		for (int i = 0; i < num; ++i) {
//			getNthChild(info, i);
//		}
//	}

	public boolean hasSomeChildVisibleInAstView(AstInfo info) {
		final int num = getNumChildren(info);
		for (int i = 0; i < num; i++) {
			if (getNthChild(info, i).shouldBeVisibleInAstView()) {
				return true;
			}
		}
		final List<String> extras = extraAstReferences(info);
		return extras != null && !extras.isEmpty();
	}

	public boolean shouldBeVisibleInAstView() {
		if (shouldBeVisibleInAstView == null) {
			shouldBeVisibleInAstView = true;
			try {
				Boolean vis = (Boolean) Reflect.invoke0(underlyingAstNode, "cpr_astVisible");
				if (vis != null) {
					shouldBeVisibleInAstView = vis;
				} else {
					System.err.println("Got null value from cpr_astVisible");
				}
			} catch (ClassCastException e) {
				System.err.println("Got non-boolean value from cpr_astVisible");
				e.printStackTrace();
			} catch (InvokeProblem e) {
				// OK, this is an optional attribute after all
			}
		}
		return shouldBeVisibleInAstView;
	}

	public int getNumChildren(AstInfo info) {
		if (children == null) {
			int numCh;
			switch (info.astApiStyle) {
			case CPR_EVERYTHING:
				numCh = (Integer) Reflect.invoke0(underlyingAstNode, "cpr_getNumChild");
				break;

			case CPR_SEPARATE_LINE_COLUMN:
			case BEAVER_PACKED_BITS: // Fall through
			case JASTADD_NO_POSITION: // Fall through
			case JASTADD_SEPARATE_LINE_COLUMN:
				numCh = (Integer) Reflect.invoke0(underlyingAstNode, "getNumChild");
				break;

			case PMD_SEPARATE_LINE_COLUMN:
				numCh = (Integer) Reflect.invoke0(underlyingAstNode, "getNumChildren");
				break;

			default:
				throw new Error("Unsupported api style " + info.astApiStyle);
			}

			this.children = new AstNode[numCh];
		}
		return this.children.length;
	}

	public AstNode getNthChild(AstInfo info, int n) {
		return getNthChild(info, n, null);
	}

	private AstNode getNthChild(AstInfo info, int n, AstNode potentialOverride) {
		final int len = getNumChildren(info);
		if (n < 0 || n >= len) {
			throw new ArrayIndexOutOfBoundsException(
					"This node has " + len + " " + (len == 1 ? "child" : "children") + ", index " + n + " is invalid");
		}
		if (children[n] == null) {
			final Object rawChild;
			switch (info.astApiStyle) {
			case CPR_EVERYTHING:
				rawChild = Reflect.invokeN(underlyingAstNode, "cpr_getChild", new Class<?>[] { Integer.TYPE },
						new Object[] { n });
				break;
			default:
				rawChild = Reflect.invokeN(underlyingAstNode, "getChild", new Class<?>[] { Integer.TYPE },
						new Object[] { n });
				break;
			}
			if (potentialOverride != null && potentialOverride.underlyingAstNode == rawChild) {
				children[n] = potentialOverride;
			} else {
				children[n] = new AstNode(rawChild);
			}
			children[n].parent = this;
			children[n].expandedParent = true;
			children[n].visitedNodes.addAll(this.visitedNodes);
		}
		return children[n];

	}

	public Iterable<AstNode> getChildren(AstInfo info) {
		final int len = getNumChildren(info);
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
						return getNthChild(info, pos++);
					}
				};
			}
		};
	}

	@Override
	public String toString() {
		return "AstNode<" + underlyingAstNode.getClass().getSimpleName() + ":" + underlyingAstNode + ">";
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

	public boolean isInsideExternalFile(AstInfo info) {
		if (info.hasOverride0(underlyingAstNode.getClass(), "cpr_isInsideExternalFile")) {
			final Object res = Reflect.invoke0(underlyingAstNode, "cpr_isInsideExternalFile");
			if (res instanceof Boolean) {
				return ((Boolean) res).booleanValue();
			}
			System.out.println("Got non-boolean from cpr_isInsideExternalFile: " + res);
			return false;
		}
		return false;
	}

	public String getNodeLabel() {
		try {
			return (String) Reflect.invoke0(underlyingAstNode, "cpr_nodeLabel");
		} catch (InvokeProblem | ClassCastException ip) {
			// OK, nodeLabel is optional
			return null;
		}
	}

	public String getAstLabel() {
		try {
			return (String) Reflect.invoke0(underlyingAstNode, "cpr_astLabel");
		} catch (InvokeProblem | ClassCastException ip) {
			// OK, astLabel is optional
			return null;
		}
	}

	public List<String> propertyListShow(AstInfo info) {
		final String mth = "cpr_propertyListShow";
		if (info.hasOverride0(underlyingAstNode.getClass(), mth)) {
			try {
				final Object override = Reflect.invoke0(underlyingAstNode, mth);
				if (override instanceof Collection<?>) {
					@SuppressWarnings("unchecked")
					final Collection<String> cast = (Collection<String>) override;
					return new ArrayList<String>(cast);
				} else if (override instanceof Object[]) {
					final String[] cast = (String[]) override;
					return Arrays.asList(cast);
				} else if (override != null) {
					System.out.println("'" + mth + "' is expected to be a collection or String array, got " + override);
				}
			} catch (InvokeProblem e) {
				System.out.println("Error when evaluating " + mth);
				e.printStackTrace();
			}
		}
		return Collections.emptyList();
	}

	public List<String> extraAstReferences(AstInfo info) {
		final String mth = "cpr_extraAstReferences";
		if (info.hasOverride0(underlyingAstNode.getClass(), mth)) {
			try {
				final Object override = Reflect.invoke0(underlyingAstNode, mth);
				if (override instanceof Collection<?>) {
					@SuppressWarnings("unchecked")
					final Collection<String> cast = (Collection<String>) override;
					return new ArrayList<String>(cast);
				} else if (override instanceof Object[]) {
					final String[] cast = (String[]) override;
					return Arrays.asList(cast);
				} else if (override != null) {
					System.out.println("'" + mth + "' is expected to be a collection or String array, got " + override);
				}
			} catch (InvokeProblem e) {
				System.out.println("Error when evaluating " + mth);
				e.printStackTrace();
			}
		}
		return Collections.emptyList();
	}

	public boolean hasProperty(AstInfo info, String propName) {
		if (info.hasOverride0(underlyingAstNode.getClass(), propName)) {
			return true;
		}
		for (String prop : propertyListShow(info)) {
			if (prop.equals(propName)) {
				return true;
			}
		}
		return false;
	}
}
