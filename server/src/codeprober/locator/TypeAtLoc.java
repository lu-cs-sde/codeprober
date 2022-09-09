package codeprober.locator;

import java.util.Objects;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.metaprogramming.InvokeProblem;

public class TypeAtLoc {

	public final String type;
	public final Span loc;
//	public final int depth;

	public TypeAtLoc(String type, Span position) {
		this.type = type;
		this.loc = position;
//		this.depth = depth;
	}

	@Override
	public String toString() {
		return type + "@[" + loc.start + ".." + loc.end + "]";
	}

	public static TypeAtLoc from(AstInfo info, AstNode astNode) throws InvokeProblem {
		return new TypeAtLoc(astNode.underlyingAstNode.getClass().getName(), astNode.getRecoveredSpan(info));
	}

	@Override
	public int hashCode() {
		return Objects.hash(loc, type);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final TypeAtLoc other = (TypeAtLoc) obj;
		return Objects.equals(loc, other.loc) && Objects.equals(type, other.type); // && Objects.equals(depth, other.depth);
	}
}
