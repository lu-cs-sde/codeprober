
package codeprober.textprobe.ast;

import java.util.function.BiConsumer;

import codeprober.textprobe.ast.ASTNodeAnnotation.Attribute;
import codeprober.textprobe.ast.ASTNodeAnnotation.Child;

public class VarDecl extends AbstractASTNode {
	public final Label name;
	public final Query src;

	public VarDecl(Position start, Position end, Label name, Query src) {
		super(start, end);
		this.name = addChild(name);
		this.src = addChild(src);
	}

	@Child(name = "name")
	public Label name() {
		return name;
	}

	@Child(name = "src")
	public Query src() {
		return src;
	}

	@Override
	@Attribute
	public String pp() {
		return String.format("$%s:=%s", name.pp(), src.pp());
	}

	@Override
	protected void doCollectProblems(BiConsumer<ASTNode, String> addErr) {
		if (doc().varDecls().get(name.value) != this) {
			addErr.accept(this, String.format("Duplicate variable '%s'", name.value));
		}
		if (src.assertion.isPresent()) {
			addErr.accept(this, "VarDecl sources cannot have assertionsa");
		}
	}
}
