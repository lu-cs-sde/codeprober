package codeprober.requesthandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.CreateLocator;
import codeprober.locator.CreateLocator.LocatorMergeMethod;
import codeprober.protocol.create.EncodeResponseValue;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.Tracing;

public class TracingBuilder implements Consumer<Object[]> {

	private class PendingTrace {
		public Object node;
		public final Property property;
		public final List<PendingTrace> dependencies = new ArrayList<>();
		public Object value;

		public PendingTrace(Object node, Property property) {
			this.node = node;
			this.property = property;
		}

		private boolean isAttached(AstNode node) {
			if (node.underlyingAstNode == info.ast.underlyingAstNode) {
				return true;
			}
			final AstNode parent = node.parent();
			if (parent == null) {
				return false;
			}
			return isAttached(parent);
		}

		public Tracing toTrace() {
			final AstNode astNode = new AstNode(node);
			if (!isAttached(astNode)) {
				// Oh dear! Pretend it did not happen
				return null;
			}
			final NodeLocator locator = CreateLocator.fromNode(info, astNode);
			if (locator == null) {
				System.err.println("Failed creating locator to " + node);
				System.out.println("Origin property: " + property.toJSON());
				return null;
			}
			final RpcBodyLine result;
			if (value == null || skipEncodingResult) {
				result = NULL_RESULT;
			} else {
				final List<RpcBodyLine> lines = new ArrayList<>();
				EncodeResponseValue.encodeTyped(info, lines, new ArrayList<>(), value, new HashSet<>());
				result = lines.size() == 1 ? lines.get(0) : RpcBodyLine.fromArr(lines);
			}
			return new Tracing(locator, property, dependencies.stream() //
					.map(pt -> pt.toTrace()) //
					.filter(t -> t != null) //
					.collect(Collectors.toList()), result);
		}
	}

	private final AstInfo info;
	private final List<PendingTrace> completed = new ArrayList<>();
	private Stack<PendingTrace> active = new Stack<>();
	private static RpcBodyLine NULL_RESULT = RpcBodyLine.fromPlain("null");

	private Set<String> excludedAstNames = new HashSet<>();
	private Set<String> excludedAttributes = new HashSet<>();
	private boolean skipEncodingResult = false;

	public TracingBuilder(AstInfo info) {
		this.info = info;
		excludedAstNames.add("ParseName"); // "Temporarily" disabled due to bug(?) in ExtendJ
	}

	public void setSkipEncodingTraceResults(boolean shouldSkip) {
		skipEncodingResult = shouldSkip;
	}

	public void addExcludedAstType(String astName) {
		excludedAstNames.add(astName);
	}

	public void addExcludedAttribute(String attrName) {
		excludedAttributes.add(attrName);
	}

	private boolean recursionProtection = false;
	private boolean acceptTraces = true;

	public void stop() {
		acceptTraces = false;
	}

	public Tracing finish(NodeLocator subjectOfTheTrace) {
		final LocatorMergeMethod mergeMethod = CreateLocator.getMergeMethod();
		CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
		try {
			final List<Tracing> result = completed.stream().map(pt -> pt.toTrace()).filter(t -> t != null)
					.collect(Collectors.toList());
			if (result.isEmpty()) {
				return null;
			}
			if (result.size() == 1) {
				return result.get(0);
			}
			// Else, the property itself did not have tracing information, but it did call
			// multiple others that have it
			// Wrap this property in a fake trace event
			return new Tracing(subjectOfTheTrace != null ? subjectOfTheTrace : result.get(1).node,
					new Property("MultipleTraceEvents"), result, RpcBodyLine.fromPlain(""));
		} finally {
			CreateLocator.setMergeMethod(mergeMethod);
		}
	}

	private boolean excludeAttribute(Object node, String attr) {
		if (excludedAstNames.contains(node.getClass().getSimpleName())) {
			return true;
		}
		if (attr.contains(".cpr_")) {
			// Internal CodeProber attribute, exclude this
			return true;
		}
		if (excludedAttributes.contains(attr)) {
			return true;
		}
		final int dot = attr.indexOf('.');
		if (dot != 1 && excludedAttributes.contains(attr.substring(dot + 1))) {
			return true;
		}

		return false;
	}

	@Override
	public void accept(Object[] args) {
		if (recursionProtection || !acceptTraces) {
			return;
		}
		recursionProtection = true;
		try {
			final String event = String.valueOf(args[0]);
			if (args.length == 0) {
				System.err.println("Invalid tracing information - empty array");
				return;
			}
			switch (event) {
			case "COMPUTE_BEGIN": {
				// Expected structure: Trace.Event event, ASTNode node, String attribute, Object
				// params, Object value
				if (args.length != 5) {
					System.err.println(
							"Invalid tracing information - expected 5 items for COMPUTE_BEGIN, got " + args.length);
					return;
				}
				final Object astNode = args[1];
				String attribute = String.valueOf(args[2]);
				if (excludeAttribute(astNode, attribute)) {
					return;
				}
				if (attribute.endsWith("(String)")) {
					final String insert = String.format("\"%s\"", String.valueOf(args[3]));
					if (insert.length() <= 16) {
						// Relatively short arg, inline the value into the attribute name.
						attribute = String.format("%s(%s)",
								attribute.substring(0, attribute.length() - "(String)".length()), insert);
					}
				} else if (attribute.endsWith("(String,int)") && args[3] instanceof List) {
					final List<?> argList = (List<?>) args[3];
					if (argList.size() == 2) {
						final String insert = String.format("\"%s\",%s", String.valueOf(argList.get(0)),
								String.valueOf(argList.get(1)));
						if (insert.length() <= 16) {
							// Relatively short arg list, inline
							attribute = String.format("%s(%s)",
									attribute.substring(0, attribute.length() - "(String,int)".length()), insert);

						}
					}
				}

//				System.out.printf("Encode %s on %s\n", attribute, astNode.getClass().getSimpleName());
//				System.out.println(args[3]); // Params
				// TODO handle params correctly
				final PendingTrace tr = new PendingTrace(astNode,
						new Property(attribute.substring(attribute.indexOf('.') + 1)));

				if (active.isEmpty()) {
					completed.add(tr);
				} else {
					active.peek().dependencies.add(tr);
				}
				active.push(tr);
				break;
			}
			case "COMPUTE_END": {
				// Expected structure: same as COMPUTE_BEGIN
				if (args.length != 5) {
					System.err.println(
							"Invalid tracing information - expected 5 items for COMPUTE_END, got " + args.length);
					return;
				}
				final Object astNode = args[1];
				final String attribute = String.valueOf(args[2]);
				if (excludeAttribute(astNode, attribute)) {
					return;
				}
				final Object value = args[4];

				final PendingTrace popped = active.pop();
				popped.value = value;
				break;
			}
			default: {
				System.err.printf("Unknown tracing event '%s'", event);
			}
			}
		} finally {
			recursionProtection = false;
		}

	}

}
