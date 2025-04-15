package codeprober.requesthandler;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Function;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.CreateLocator;
import codeprober.locator.CreateLocator.LocatorMergeMethod;
import codeprober.protocol.create.CreateValue;
import codeprober.protocol.create.EncodeResponseValue;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.Tracing;
import codeprober.util.BenchmarkTimer;

public class TracingBuilder implements Consumer<Object[]> {

	public class PendingTrace {
		public Object node;
		private final AstNode astNode;
		private NodeLocator nodeLocator;
		private RpcBodyLine encodedResult;
		public final Property property;
		public final List<PendingTrace> dependencies = new ArrayList<>();
		public Object value;
		public final Object preTraceValue;
		public final PendingTrace parent;
		public final Object[] computeBeginArgs;

		/**
		 * Unused field, available for subtypes of {@link TracingBuilder} to store
		 * arbitrary data.
		 */
		public Object userData = null;

		public PendingTrace(Object node, Property property, Object preTraceValue, PendingTrace parent,
				Object[] computeBeginArgs
		) {
			this.node = node;
			this.astNode = new AstNode(node);
			this.property = property;
			this.preTraceValue = preTraceValue;
			this.parent = parent;
			this.computeBeginArgs = computeBeginArgs;
		}

		public NodeLocator getLocator() {
			if (nodeLocator == null) {
				nodeLocator = CreateLocator.fromNode(info, astNode);
			}
			return nodeLocator;
		}

		public RpcBodyLine getEncodedResult() {
			if (encodedResult == null) {
				encodedResult = encodeValue(value);
			}
			return encodedResult;
		}

		@Override
		public String toString() {
			return "PT:" + node.getClass().getSimpleName() + "." + property.name;
		}

		public Tracing toTrace() {
			if (!isAttached(astNode)) {
				// Oh dear! Pretend it did not happen;
				return null;
			}
			final NodeLocator locator = getLocator();
			if (locator == null) {
				System.err.println("Failed creating locator to " + node);
				System.out.println("Origin property: " + property.toJSON());
				return null;
			}
			final RpcBodyLine result = getEncodedResult();
			final List<Tracing> depList;
			if (dependencies.size() == 1) {
				// Common case shortcut
				final Tracing ptt = dependencies.get(0).toTrace();
				if (ptt == null) {
					depList = Collections.emptyList();
				} else {
					depList = Collections.singletonList(ptt);
				}
			} else {
				depList = new ArrayList<>();
				for (PendingTrace pt : dependencies) {
					final Tracing ptt = pt.toTrace();
					if (ptt != null) {
						depList.add(ptt);
					}
				}
			}
			return new Tracing(locator, property, depList, result);
		}
	}

	private final AstInfo info;
	private final List<PendingTrace> completed = new ArrayList<>();
	private Stack<PendingTrace> active = new Stack<>();
	public static RpcBodyLine NULL_RESULT = RpcBodyLine.fromPlain("null");

	private Set<String> excludedAstNames = new HashSet<>();
	private Set<String> excludedAttributes = new HashSet<>();
	private boolean skipEncodingResult = false;
	private List<PropertyArg> fallbackArgsForUnknownArgumentTypes = null;
	private final IdentityHashMap<Object, Boolean> isAttachedCheckCache = new IdentityHashMap<>();
	final HashSet<Object> encodeAlreadyVisitedNodesCache = new HashSet<>();
	private final List<RpcBodyLine> encodeLinesCache = new ArrayList<>();

	private static Set<String> alreadyWarnedAttributesFailedArgumentDeterming = new HashSet<>();

	private boolean recursionProtection = false;
	private boolean acceptTraces = true;

	public TracingBuilder(AstInfo info) {
		this.info = info;
		excludedAstNames.add("ParseName"); // "Temporarily" disabled due to bug(?) in ExtendJ
	}

	public void resetActiveStack() {
		active.clear();
	}

	public RpcBodyLine encodeValue(Object value) {
		if (value == null || skipEncodingResult) {
			return NULL_RESULT;
		} else {
			encodeLinesCache.clear();
			encodeAlreadyVisitedNodesCache.clear();
			EncodeResponseValue.encodeTyped(info, encodeLinesCache, null, value, encodeAlreadyVisitedNodesCache);
			final RpcBodyLine ret = encodeLinesCache.size() == 1 ? encodeLinesCache.get(0)
					: RpcBodyLine.fromArr(new ArrayList<>(encodeLinesCache));
			return ret;
		}
	}

	public boolean isAttached(AstNode node) {
		if (node.underlyingAstNode == info.ast.underlyingAstNode) {
			return true;
		}
		final AstNode parent = node.parent();
		if (parent == null) {
			return false;
		}
		final Boolean cached = isAttachedCheckCache.get(parent.underlyingAstNode);
		if (cached != null) {
			return cached;
		}
		final boolean ret = isAttached(parent);
		isAttachedCheckCache.put(parent.underlyingAstNode, ret);
		return ret;
	}

	public void setSkipEncodingTraceResults(boolean shouldSkip) {
		skipEncodingResult = shouldSkip;
	}

	public void setFallbackArgsForUnknownArgumentTypes(List<PropertyArg> args) {
		fallbackArgsForUnknownArgumentTypes = args;
	}

	public void addExcludedAstType(String astName) {
		excludedAstNames.add(astName);
	}

	public void addExcludedAttribute(String attrName) {
		excludedAttributes.add(attrName);
	}

	public void stop() {
		acceptTraces = false;
	}

	public Tracing finish(NodeLocator subjectOfTheTrace) {
		final LocatorMergeMethod mergeMethod = CreateLocator.getMergeMethod();
		CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
		try {
			if (completed.size() == 1) {
				// Common case shortcut
				return completed.get(0).toTrace();
			}
			final List<Tracing> result = new ArrayList<>();
			for (PendingTrace pt : completed) {
				final Tracing ptt = pt.toTrace();
				if (ptt != null) {
					result.add(ptt);
				}
			}
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

	protected boolean excludeAttribute(Object node, String attr) {
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

	/**
	 * Called when a COMPUTE_BEGIN event happens. Can be used to compute extra
	 * information before a traced property finishes computing. The returned value
	 * will be passed to {@link #onComputeEnd(Object, Property, Object)}.
	 * <p>
	 * Default implementation always returns <code>null</code>.
	 *
	 * @param node     the node related to the trace.
	 * @param property the property being computed.
	 * @param args     the argument(s) (if any) to the property
	 * @return a value to be passed to
	 *         {@link #onComputeEnd(Object, Property, Object)}. If
	 *         <code>null</code>, then
	 *         {@link #onComputeEnd(Object, Property, Object)} will not be called.
	 */
	protected Object getComputeBeginInformation(Object node, Property property, Object args) {
		return null;
	}

	/**
	 * Called in response to a COMPUTE_END event, if a non-<code>null</code> value
	 * was returned by
	 * {@link #getComputeBeginInformation(Object, Property, Object)}.
	 * <p>
	 * Default implementation is a no-op.
	 *
	 * @param result           the result of the computed property.
	 * @param computeBeginInfo the info from
	 *                         {@link #getComputeBeginInformation(Object, Property, Object)}.
	 */
	protected void onComputeEnd(Object result, Object computeBeginInfo) {
		// Noop
	}

	@Override
	public void accept(Object[] args) {
		if (recursionProtection || !acceptTraces) {
			return;
		}

		recursionProtection = true;
		try {
			if (args.length == 0) {
				System.err.println("Invalid tracing information - empty array");
				return;
			}
			final String event = String.valueOf(args[0]);
			switch (event) {
			case "COMPUTE_BEGIN": {
				/**
				 * Expected structure:
				 * <ul>
				 * <li>0: Trace.Event event
				 * <li>1: ASTNode node
				 * <li>2: String attribute
				 * <li>3: Object params
				 * <li>4: Object value
				 * </ul>
				 */
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

				final List<PropertyArg> propArgs = decodeTraceArgs(astNode, attribute, args[3]);

				final Property prop = new Property(attribute.substring(attribute.indexOf('.') + 1), propArgs);
				final PendingTrace top = active.isEmpty() ? null : active.peek();
				final PendingTrace tr;
				tr = new PendingTrace(astNode, prop, getComputeBeginInformation(astNode, prop, args[3]), top, args);
				if (top == null) {
					completed.add(tr);
				} else {
					top.dependencies.add(tr);
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
				if (active.isEmpty()) {
					System.out.println("!! Got COMPUTE_END on empty stack..");
					Thread.dumpStack();
//					dumpLastAcceptsInfo();
					System.exit(1);
				}

				PendingTrace popped = active.pop();
				// Check if there is a missing COMPUTE_END, which can be caused by a `throw`
				// Intentional identity comparisons, they should work fine while being
				// performant
				while (popped.computeBeginArgs[1] != args[1] //
						|| !popped.computeBeginArgs[2].equals(args[2])//
						|| popped.computeBeginArgs[3] != args[3]) {
					System.out.println("Synthesizing missing COMPUTE_END event for " + popped.computeBeginArgs[2]);
					if (popped.preTraceValue != null) {
						onComputeEnd(null, popped.preTraceValue);
					}
					popped = active.pop();
				}
				final Object value = args[4];
				if (popped.preTraceValue != null) {
					onComputeEnd(value, popped.preTraceValue);
				}
				popped.value = value;
				break;
			}
			default: {
//				System.err.printf("Unknown tracing event '%s'%n", event);
				break;
			}
			}
		} finally {
			recursionProtection = false;
		}

	}

	protected PendingTrace peekActiveTrace() {
		return active.isEmpty() ? null : active.peek();
	}

	private static class ArgsKey {
		private final Class<?> owner;
		private final String attribute;
		private final int numParams;

		public ArgsKey(Class<?> owner, String attribute, int numParams) {
			this.owner = owner;
			this.attribute = attribute;
			this.numParams = numParams;
		}

		@Override
		public int hashCode() {
			return Objects.hash(attribute, numParams, owner);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ArgsKey other = (ArgsKey) obj;
			return Objects.equals(attribute, other.attribute) && numParams == other.numParams
					&& Objects.equals(owner, other.owner);
		}
	}

	private final Map<ArgsKey, Function<List<?>, List<PropertyArg>>> traceArgsDecodingCache = new HashMap<>();

	public List<PropertyArg> decodeTraceArgs(Object astNode, String attribute, Object rawParams) {
		if (rawParams == null || attribute.endsWith("()")) {
			return null;
		}
		final List<?> paramsAsList = attribute.contains(",") ? (List<?>) rawParams : Arrays.asList(rawParams);
		final ArgsKey key = new ArgsKey(astNode.getClass(), attribute, paramsAsList.size());
		if (!traceArgsDecodingCache.containsKey(key)) {
			final String shortNeedle = attribute.substring(attribute.indexOf('.') + 1, attribute.indexOf('('));

			List<Method> candidates = new ArrayList<>();
			for (Method m : astNode.getClass().getMethods()) {
				if (!m.getName().equals(shortNeedle)) {
					continue;
				}
				if (key.numParams != m.getParameterCount()) {
					continue;
				}

				candidates.add(m);
			}
			if (candidates.isEmpty()) {
				if (alreadyWarnedAttributesFailedArgumentDeterming.add(attribute)) {
					System.err.println("Failed determining types of arguments to " + attribute);
				}
				traceArgsDecodingCache.put(key, null);
			} else {
				traceArgsDecodingCache.put(key, args -> {
					testCandidate: for (Method m : candidates) {
						final Class<?>[] params = m.getParameterTypes();
						final Type[] genParams = m.getGenericParameterTypes();

						final List<PropertyArg> convArgs = new ArrayList<>();
						for (int argIdx = 0; argIdx < args.size(); ++argIdx) {
							final Object argVal = args.get(argIdx);
							if (argVal != null) {
								if (params[argIdx] == Integer.TYPE && argVal instanceof Integer) {
									// OK
								} else if (!params[argIdx].isInstance(argVal)) {
									continue testCandidate;
								}
							}
							final PropertyArg val = CreateValue.fromInstance(info, params[argIdx], genParams[argIdx],
									argVal);
							if (val == null) {
								continue testCandidate;
							}
							convArgs.add(val);
						}
						return convArgs;
					}
					if (alreadyWarnedAttributesFailedArgumentDeterming.add(attribute)) {
						System.err.println("Failed determining types of arguments to " + attribute);
					}
					return null;
				});
			}
		}
		final Function<List<?>, List<PropertyArg>> decoder = traceArgsDecodingCache.get(key);
		if (decoder != null) {
			final List<PropertyArg> decoded = decoder.apply(paramsAsList);
			if (decoded != null) {
				return decoded;
			}
		}

		return fallbackArgsForUnknownArgumentTypes;
	}
}
