package codeprober.metaprogramming;

import codeprober.ast.AstNode;

public interface TypeIdentifier {
	boolean matchesDesiredType(AstNode node);
}
