package codeprober.textprobe.ast;

import codeprober.textprobe.ast.ASTNodeAnnotation.Attribute;
import codeprober.textprobe.ast.ASTNodeAnnotation.Child;

public class QueryAssert extends AbstractASTNode {
	public final boolean exclamation;
	public final boolean tilde;
	public final Position eq;
	public final ExpectedValue expectedValue;

	public QueryAssert(Position start, Position end, boolean exclamation, boolean tilde, Position eq, ExpectedValue expectedValue) {
		super(start, end);
		this.exclamation = exclamation;
		this.tilde = tilde;
		this.eq = eq;
		this.expectedValue = addChild(expectedValue);
	}

	@Child(name = "expectedValue")
	public ExpectedValue expectedValue() {
		return expectedValue;
	}

	@Attribute
	public boolean negate() {
		return exclamation;
	}

	@Attribute
	public boolean contains() {
		return tilde;
	}

	@Override
	@Attribute
	public String pp() {
		return String.format("%s%s=%s", //
				exclamation ? "!" : "", //
				tilde ? "~" : "", //
				expectedValue.pp() //
		);
	}

}
