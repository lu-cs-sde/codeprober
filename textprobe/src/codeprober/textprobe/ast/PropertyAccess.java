package codeprober.textprobe.ast;

import java.util.List;
import java.util.stream.Collectors;

import codeprober.textprobe.ast.ASTNodeAnnotation.Child;

public class PropertyAccess extends AbstractASTNode {
	public final Label name;
	public final Opt<ASTList<Argument>> arguments;

	public PropertyAccess(Position start, Position end, Label name) {
		this(start, end, name, null, null);
	}

	public PropertyAccess(Position start, Position end, Label name, Position argStart, List<Argument> arguments) {
		super(start, end);
		this.name = addChild(name);
		if (argStart == null || arguments == null) {
			this.arguments = addChild(new Opt<>());
		} else {
			final ASTList<Argument> argList = new ASTList<Argument>(argStart, end);
			for (Argument arg : arguments) {
				argList.add(arg);
			}
			this.arguments = addChild(new Opt<>(argList));
		}
	}

	@Child(name = "name")
	public Label name() {
		return name;
	}

	@Child(name = "arguments")
	public ASTList<Argument> arguments() {
		return arguments.get();
	}

	@Override
	public String pp() {
		if (!arguments.isPresent()) {
			return name.pp();
		}
		return String.format("%s(%s)", name.pp(),
				arguments.get().stream().map(x -> x.pp()).collect(Collectors.joining(", ")));
	}
}
