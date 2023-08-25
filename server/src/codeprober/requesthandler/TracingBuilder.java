package codeprober.requesthandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

		public Tracing toTrace(String prefix) {
			final AstNode astNode = new AstNode(node);
			if (!isAttached(astNode)) {
				// Oh dear! Pretend it did not happen
				return null;
			}
//			System.out.println(prefix + " Encode attached node " + node.getClass().getSimpleName() +": " + node + " . " + property.toJSON() +" -> " + (value != null ? value.getClass().getSimpleName() : "null") +": " + value);
			final NodeLocator locator = CreateLocator.fromNode(info, astNode);
			if (locator == null) {
				System.err.println("Failed creating locator to " + node);
				System.exit(1);
			}
			final RpcBodyLine result;
			if (value == null) {
				result = NULL_RESULT;
			} else {
				final List<RpcBodyLine> lines = new ArrayList<>();
//				final List
				EncodeResponseValue.encodeTyped(info, lines, new ArrayList<>(), value, new HashSet<>());
				result = lines.size() == 1 ? lines.get(0) : RpcBodyLine.fromArr(lines);
//				result = RpcBodyLine.fromArr(lines);
			}
			return new Tracing(locator, property, dependencies.stream() //
					.map(pt -> pt.toTrace(prefix + property.name + " > ")) //
					.filter(t -> t != null) //
					.collect(Collectors.toList()), result);
		}
	}

	private final AstInfo info;
	private final List<PendingTrace> completed = new ArrayList<>();
	private Stack<PendingTrace> active = new Stack<>();
	private static RpcBodyLine NULL_RESULT = RpcBodyLine.fromPlain("null");

	public TracingBuilder(AstInfo info) {
		this.info = info;
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
			final List<Tracing> result = completed.stream().map(pt -> pt.toTrace("")).filter(t -> t != null)
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
			return new Tracing(subjectOfTheTrace, new Property("MultipleTraceEvents", new ArrayList<>(), null), result,
					RpcBodyLine.fromPlain(""));
		} finally {
			CreateLocator.setMergeMethod(mergeMethod);
		}
	}

	private static boolean excludeAttribute(Object node, String attr) {
		if (node.getClass().getSimpleName().equals("ParseName")) {
			return true;
		}
//		if (attr.endsWith(".rewrittenNode()")) {
//			return true;
//		}
//		if (attr.endsWith(".canResolve()")) {
//			return true;
//		}
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
//			String prefix = "";
//			if (nesting != 0) {
//				for (int i = 0; i < nesting - 1; ++i) {
//					prefix += "│ ";
//				}
//				prefix += String.format("%s─", event.equals("COMPUTE_END") ? "└" : "├");
//			}

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
				final String attribute = String.valueOf(args[2]);
				if (attribute.contains(".cpr_")) {
					// CodeProber-specific functionality, ignore
//					return;
				}
				if (excludeAttribute(astNode, attribute)) {
					return;
				}

//				System.out.printf("Encode %s on %s\n", attribute, astNode.getClass().getSimpleName());
//				System.out.println(args[3]); // Params
				// TODO handle params correctly
				final PendingTrace tr = new PendingTrace(astNode,
						new Property(attribute.substring(attribute.indexOf('.') + 1), Collections.emptyList(), null));

				if (active.isEmpty()) {
					completed.add(tr);
				} else {
					active.peek().dependencies.add(tr);
				}
				active.push(tr);
//				body.add(RpcBodyLine
//						.fromStreamArg(String.format("%s%s %s", prefix, nesting == 0 ? "┌" : "┬", attribute)));
//				++nesting;
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
				final Object value = args[4];
				if (attribute.contains(".cpr_")) {
					// CodeProber-specific functionality, ignore
//					return;
				}
//				System.out.printf("COMPUTE_END '%s' -> %s | %s\n", attribute,
//						value + " | " + (value != null ? value.getClass() : ""),
//						Arrays.toString(args));

				if (excludeAttribute(astNode, attribute)) {
					return;
				}
//				body.add(RpcBodyLine.fromStreamArg(String.format("%s─> %s", prefix, String.valueOf(value))));
				final PendingTrace popped = active.pop();
				popped.value = value;
//				body.add(RpcBodyLine.fromStreamArg(String.format("%s trace:end %s on a %s", prefix,
//						attribute, astNode.getClass().getSimpleName())));
//				--nesting;
				break;
			}
			default: {
				System.err.printf("Unknown event '%s'", event);
//				body.add(RpcBodyLine
//						.fromStreamArg(String.format("%s trace: unknown event '%s'", prefix, String.valueOf(args[0]))));
			}
			}
		} finally {
			recursionProtection = false;
		}

	}

}
