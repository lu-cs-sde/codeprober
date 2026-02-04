package codeprober.textprobe.ast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import codeprober.textprobe.CompletionContext;
import codeprober.textprobe.ast.ASTNodeAnnotation.Attribute;
import codeprober.textprobe.ast.ASTNodeAnnotation.Child;
import codeprober.textprobe.ast.Argument.ArgumentType;
import codeprober.textprobe.ast.Probe.Type;

public class Document extends AbstractASTNode {

	public final ASTList<Container> containers = addChild(new ASTList<Container>());

	// Attribute cache
	private Map<String, VarDecl> varDecls_value;

	public Document(Position start, Position end, List<Container> containers) {
		super(start, end);

		for (Container c : containers) {
			this.containers.add(c);
		}
	}

	@Attribute
	public Document doc() {
		return this;
	}

	@Child(name = "containers")
	public ASTList<Container> containers() {
		return containers;
	}

	@Attribute
	public Map<String, VarDecl> varDecls() {
		if (varDecls_value == null) {
			varDecls_value = new HashMap<>();
			for (Container c : containers) {
				final Probe probe = c.probe();
				if (probe != null && probe.type == Type.VARDECL) {
					VarDecl dec = probe.asVarDecl();
					varDecls_value.putIfAbsent(dec.name.value, dec);
				}
			}
		}
		return varDecls_value;
	}

	public VarDecl getVar(String name) {
		return varDecls().get(name);
	}

	@Attribute
	public Container containerAt(int line, int column) {
		for (Container c : containers) {
			if (c.start.line <= line && c.end.line >= line && c.start.column <= column && c.end.column >= column) {
				return c;
			}
		}
		return null;
	}

	private Set<Integer> bumpedLines_value;

	public Set<Integer> bumpedLines() {
		if (bumpedLines_value == null) {
			bumpedLines_value = new HashSet<>();
			for (Container c : containers) {
				Probe p = c.probe();
				if (p == null) {
					continue;
				}
				final QueryHead head = p.type == Probe.Type.QUERY //
						? p.asQuery().head //
						: (p.type == Probe.Type.VARDECL ? p.asVarDecl().src.head : null);
				if (head != null && head.type == QueryHead.Type.TYPE && head.asType().bumpUp) {
					bumpedLines_value.add(head.start.line);
				}
			}
		}
		return bumpedLines_value;
	}

	@Attribute
	public Set<String> problems() {
		Set<String> ret = new TreeSet<>();
		BiConsumer<ASTNode, String> addErr = (node, msg) -> ret.add(String.format("%s %s", node.loc(), msg));
		collectProblems(addErr);
		return ret;
	}

	@Override
	protected void doCollectProblems(BiConsumer<ASTNode, String> addErr) {
		final Map<String, VarDecl> decls = varDecls();
		Set<VarDecl> visited = new HashSet<>();
		Set<VarDecl> stack = new HashSet<>();
		decls.forEach((key, dec) -> {
			checkCircularVar(key, visited, stack, addErr);
		});
	}

	private void checkCircularVar(String origin, Set<VarDecl> visited, Set<VarDecl> visitStack,
			BiConsumer<ASTNode, String> addErr) {
		final VarDecl dec = varDecls().get(origin);
		if (visited.contains(dec)) {
			return;
		}
		visited.add(dec);
		visitStack.add(dec);
		dec.forEachDescendant(child -> {
			if (child instanceof VarUse) {
				VarDecl dep = ((VarUse) child).decl();
				if (dep == null) {
					return;
				}

				if (!visited.contains(dep)) {
					checkCircularVar(dep.name.value, visited, visitStack, addErr);
				} else if (visitStack.contains(dep)) {
					addErr.accept(dec, String.format("Circular definition of '%s'", dep.name.value));
				}
			}
		});
		visitStack.remove(dec);
	}

	public CompletionContext completionContextAt(int line, int col) {
		final Container container = containerAt(line, col);
		if (container == null) {
			return null;
		}

		// Inside a container. Question is - where inside?
		if (col <= container.start.column + 1 || col >= container.end.column) {
			// We are inside one of the two '[['/']]' boundaries, or on their outside. No
			// completion here
			return null;
		}

		if (container.contents.isEmpty() || container.contents.equals("^")) {
			// We are inside an near-empty (perhaps newly created) container
			// Complete types!
			return CompletionContext.fromType(null);

		}
		if (container.probe() == null) {
			// Inside nonempty container, but it has invalid syntax. Ignore it
			return null;
		}

		// Inside a nonempty container with an existing Probe
		final Probe probe = container.probe();

		final Predicate<ASTNode> isInsideOrEnd = node -> col >= node.start.column && col <= node.end.column + 1;
		final Predicate<ASTNode> isInside = node -> col >= node.start.column && col <= node.end.column;
		switch (probe.type) {
		case QUERY:
			return completeQuery(col, isInsideOrEnd, isInside, probe.asQuery());

		case VARDECL:
			VarDecl vd = probe.asVarDecl();
			if (isInside.test(vd.name)) {
				return CompletionContext.fromVarDeclName(vd);
			}
			if (isInsideOrEnd.test(vd.src)) {
				return completeQuery(col, isInsideOrEnd, isInside, vd.src);
			}
			return null;

		default:
			System.err.println("Unknown probe type " + probe);
			return null;

		}

	}

	private CompletionContext completeQuery(int col, Predicate<ASTNode> isInsideOrEnd, Predicate<ASTNode> isInside,
			Query q) {
		if (isInsideOrEnd.test(q.head)) {
			return CompletionContext.fromType(q);
		}

		for (PropertyAccess acc : q.tail) {
			if (!isInsideOrEnd.test(acc)) {
				continue;
			}
			// Accessing this property step. Question is now: is it the property name, or an
			// argument?

			if (isInsideOrEnd.test(acc.name)) {
				// Accessing property name
				return CompletionContext.fromPropertyName(q, acc);
			}

			if (acc.arguments.isPresent() && isInside.test(acc.arguments.get())) {
				// In an existing arg?
				for (Argument arg : acc.arguments.get()) {
					if (isInsideOrEnd.test(arg)) {
						if (arg.type == ArgumentType.QUERY) {
							return completeQuery(col, isInsideOrEnd, isInside, arg.asQuery());
						}
						return null;
					}
				}
			}

			if (acc.arguments.isPresent()) {
				final ASTList<Argument> args = acc.arguments();
				if (col > args.start.column && (args.isEmpty() || args.get(args.getNumChild() - 1).end.column < col)) {
					// We are after the last arg, or inside an empty list.
					// Question is: has the last arg been "closed" yet (with a ',')?
					// Let's search backwards
					final Container container = q.enclosingContainer();
					final int contentsOffset = container.start.column + 2;
					for (int searchCol = col - 1; searchCol >= q.start.column; --searchCol) {
						final char c = container.contents.charAt(searchCol - contentsOffset);
						switch (c) {
						case ' ':
							// Keep looking
							continue;
						case '(':
						case ',':
							// Got it!
							return CompletionContext.fromNewPropertyArg(q);

						default:
							// Unknown, exit
							return null;
						}
					}
				}
			}
			// Within a property, but could not find where exactly. Ignore
			return null;
		}

		if (q.assertion.isPresent()) {
			final QueryAssert aq = q.assertion.get();
			if (isInsideOrEnd.test(aq) && col > aq.eq.column) {
				if (!q.doc().problems().isEmpty()) {
					// Cannot reliably evaluate queries when there are semantic errors
					return null;
				}

				ExpectedValue exp = aq.expectedValue;
				if (exp.type == ExpectedValue.Type.QUERY) {
					final Query rhsQuery = exp.asQuery();
					return completeQuery(col, isInsideOrEnd, isInside, rhsQuery);
				}
				// Else, right side is a constant string. Complete to the string repr of the
				// left side
				return CompletionContext.fromQueryResult(q);
			}
		}

		final Container container = q.enclosingContainer();
		final int contentsOffset = container.start.column + 2;
		if (
		//
		(col == q.end.column + 1 && container.contents.charAt(q.end.column - contentsOffset) == '.')
				//
				|| (q.assertion.isPresent() //
						&& q.start.column + container.contents.length() >= col //
						&& col == q.assertion.get().start.column //
						&& container.contents.charAt(q.assertion.get().start.column - 1 - q.start.column) == '.')) {
			return CompletionContext.fromPropertyName(q, null);
		}
		return null;
	}

	@Attribute
	public String pp() {
		return "";
	}
}
