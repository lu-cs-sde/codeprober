package codeprober.textprobe.ast;

import codeprober.textprobe.ast.ASTNodeAnnotation.Attribute;
import codeprober.textprobe.ast.ASTNodeAnnotation.Child;

// A text probe wrapper. Represents the "[[" and "]]" that surrounds the contents of a real text probe.
public class Container extends AbstractASTNode {
	public final String contents;
	public final Opt<Probe> probe;

	public Container(Position start, Position end, String contents) {
		this(start, end, contents, null);
	}

	public Container(Position start, Position end, String contents, Probe probe) {
		super(start, end);
		this.contents = contents;
		this.probe = addChild(new Opt<>(probe));
	}

	@Child(name = "probe")
	public Probe probe() {
		return probe.isPresent() ? probe.get() : null;
	}

	@Attribute
	public String contents() {
		return contents;
	}

	@Override
	public Container enclosingContainer() {
		return this;
	}
}
