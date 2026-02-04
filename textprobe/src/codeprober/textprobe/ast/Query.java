package codeprober.textprobe.ast;

import java.util.List;
import java.util.function.BiConsumer;

import codeprober.textprobe.ast.ASTNodeAnnotation.Attribute;
import codeprober.textprobe.ast.ASTNodeAnnotation.Child;
import codeprober.textprobe.ast.QueryHead.Type;

public class Query extends AbstractASTNode {
	public final QueryHead head;
	public final Integer index;
	public final ASTList<PropertyAccess> tail;
	public final Opt<QueryAssert> assertion;

	public Query(Position start, Position end, QueryHead head, Integer index, List<PropertyAccess> tail) {
		this(start, end, head, index, tail, null);
	}

	public Query(Position start, Position end, QueryHead head, Integer index, List<PropertyAccess> tail,
			QueryAssert assertion) {
		super(start, end);
		this.head = addChild(head);
		this.index = index;
		this.tail = addChild(new ASTList<>());
		for (PropertyAccess l : tail) {
			this.tail.add(l);
		}
		this.assertion = addChild(new Opt<>(assertion));
	}

	@Attribute
	public boolean isArgument() {
		ASTNode parent = getParent();
		while (parent != null) {
			if (parent instanceof PropertyAccess) {
				return true;
			}
			parent = parent.getParent();
		}
		return false;
	}

	@Child(name = "head")
	public QueryHead head() {
		return head;
	}

	@Child(name = "tail")
	public ASTList<PropertyAccess> tail() {
		return tail;
	}

	@Child(name = "assertion")
	public QueryAssert assertion() {
		return assertion.isPresent() ? assertion.get() : null;
	}

	@Override
	@Attribute
	public String pp() {
		String ret = head.pp();
		if (index != null) {
			ret = String.format("%s[%d]", ret, index);
		}
		for (PropertyAccess step : tail) {
			ret = ret + "." + step.pp();
		}
		if (assertion.isPresent()) {
			ret += assertion.get().pp();
		}

		return ret;
	}

	@Override
	protected void doCollectProblems(BiConsumer<ASTNode, String> addErr) {
		if (head.type == Type.VAR && index != null) {
			addErr.accept(head, "Cannot mix var ref and indexing");
		}
	}

	@Attribute
	public String source() {
		final Container con = enclosingContainer();
		if (con == null) {
			return null;
		}
		final int relStart = start.column - con.start.column - 2; // 2 for '[['
		final int len = end.column - start.column + 1;
		if (con.contents.length() < relStart || relStart+len > con.contents.length()) {
			return null;
		}
		return con.contents.substring(relStart, relStart + len);
	}
}
