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
//
//	private static enum CircularTraceState {
//		/**
//		 * An event like CIRCULAR_CASE1_START has been received.
//		 * <p>
//		 * This transitions to {@link #MID_COMPUTE} when COMPUTE_BEGIN happens.
//		 */
//		IDLE,
//
//		/**
//		 * In between compute_start and compute_end
//		 */
//		MID_COMPUTE,
//	}

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
//		public CircularTraceState circularState;
		public final Object[] computeBeginArgs;

		/**
		 * Unused field, available for subtypes of {@link TracingBuilder} to store
		 * arbitrary data.
		 */
		public Object userData = null;

		public PendingTrace(Object node, Property property, Object preTraceValue, PendingTrace parent,
				Object[] computeBeginArgs
//				CircularTraceState circularState
		) {
			this.node = node;
			this.astNode = new AstNode(node);
			this.property = property;
			this.preTraceValue = preTraceValue;
			this.parent = parent;
			this.computeBeginArgs = computeBeginArgs;
//			this.circularState = circularState;
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
//			final AstNode astNode = new AstNode(node);
			BenchmarkTimer.TRACE_CHECK_NODE_ATTACHMENT.enter();
			if (!isAttached(astNode)) {
				// Oh dear! Pretend it did not happen;
				BenchmarkTimer.TRACE_CHECK_NODE_ATTACHMENT.exit();
				return null;
			}
			BenchmarkTimer.TRACE_CHECK_NODE_ATTACHMENT.exit();
			final NodeLocator locator = getLocator();
			if (locator == null) {
				System.err.println("Failed creating locator to " + node);
				System.out.println("Origin property: " + property.toJSON());
				return null;
			}
			final RpcBodyLine result = getEncodedResult();
//				if (debDepthCounter >= 100) {
//					System.out.println("!");
//				}
//				return new Tracing(locator, property, dependencies.stream() //
//						.map(pt -> pt.toTrace()) //
//						.filter(t -> t != null) //
//						.collect(Collectors.toList()), result);

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
//	private boolean nextComputeBeginIsCircular = false;

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
			BenchmarkTimer.TRACE_ENCODE_RESPONSE_VALUE.enter();
			encodeLinesCache.clear();
			encodeAlreadyVisitedNodesCache.clear();
			EncodeResponseValue.encodeTyped(info, encodeLinesCache, null, value, encodeAlreadyVisitedNodesCache);
			final RpcBodyLine ret = encodeLinesCache.size() == 1 ? encodeLinesCache.get(0)
					: RpcBodyLine.fromArr(new ArrayList<>(encodeLinesCache));
			BenchmarkTimer.TRACE_ENCODE_RESPONSE_VALUE.exit();
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

//	int computeDepth = 0;
//	private boolean nextComputeIsCircular = false;

	private final Object[][] lastAcceptRingBuffer = new Object[2 * 8192][];
	private int lastAcceptRingBufferNextPos;
	private int lastAcceptRingBufferLength;
	private int totalNumAccepts;

//	public static boolean beVerboseNextAccept = false;
	private final long creationTime = System.currentTimeMillis();

	@Override
	public void accept(Object[] args) {
		if (recursionProtection || !acceptTraces) {
			return;
		}

		recursionProtection = true;
		try {
//			recordAcceptedArgs(args);
//			if (beVerboseNextAccept) {
//				beVerboseNextAccept = false;
//				System.out.println("!! VERBOSE TIME !!");
//				System.out.println("Just received " + Arrays.toString(args));
//				System.out.println("Have received " + totalNumAccepts + " so far");
//				System.out.println("Was created " + (System.currentTimeMillis() - creationTime) + "ms ago");
//				dumpLastAcceptsInfo();
//			}
			if (args.length == 0) {
				System.err.println("Invalid tracing information - empty array");
				return;
			}
			final String event = String.valueOf(args[0]);
			switch (event) {
			case "COMPUTE_BEGIN": {
//				System.out.printf("%3d COMPUTE_BEGIN %s.%s%n", ++computeDepth, args[1] + "", args[2] + "");
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
//				public static PropertyArg fromInstance(AstInfo info, Class<?> valueClazz, Type valueType, Object value) {
//				boolean markAsFailedDeterminingArgs = false;

				final List<PropertyArg> propArgs = decodeTraceArgs(astNode, attribute, args[3]);
//				System.out.println(active.size() +" > " +event +" " + astNode.getClass().getSimpleName() +" | " + attribute);
				// TODO update client side to not require string literal in attribute name
				// anymore
//				BenchmarkTimer.TRACE_CHECK_PARAMETERS.enter();
//				if (attribute.endsWith("(String)")) {
//					final String argVal = String.valueOf(args[3]);
//					propArgs = Arrays.asList(PropertyArg.fromString(argVal));
//					final String insert = String.format("\"%s\"", argVal);
//					if (insert.length() <= 16) {
//						// Relatively short arg, inline the value into the attribute name.
//						attribute = String.format("%s(%s)",
//								attribute.substring(0, attribute.length() - "(String)".length()), insert);
//					}
//				} else if (attribute.endsWith("(String,int)") && args[3] instanceof List) {
//					final List<?> argList = (List<?>) args[3];
//					if (argList.size() == 2) {
//						final String strArg = String.valueOf(argList.get(0));
//						final Integer intVal = (Integer) argList.get(1);
//						final String insert = String.format("\"%s\",%s", strArg, String.valueOf(intVal));
//						propArgs = Arrays.asList(PropertyArg.fromString(strArg), PropertyArg.fromInteger(intVal));
//						if (insert.length() <= 16) {
//							// Relatively short arg list, inline
//							attribute = String.format("%s(%s)",
//									attribute.substring(0, attribute.length() - "(String,int)".length()), insert);
//
//						}
//					}
//				} else {
//					propArgs = decodeTraceArgs(astNode, attribute, args[3]);
//				}
//				BenchmarkTimer.TRACE_CHECK_PARAMETERS.exit();
//				if (propArgs != null) {
//					System.out.println("Decided that " + attribute + " contains " + propArgs.size()
//							+ " arg(s), which are: "
//							+ propArgs.stream().map(x -> x.toJSON().toString()).collect(Collectors.joining(",")));
//				}

				final Property prop = new Property(attribute.substring(attribute.indexOf('.') + 1), propArgs);
				final PendingTrace top = active.isEmpty() ? null : active.peek();
				final PendingTrace tr;
//				if (nextComputeIsCircular) {
//					nextComputeIsCircular = false;
//					tr = new PendingTrace(astNode, prop, getComputeBeginInformation(astNode, prop, args[3]), top,
//							CircularTraceState.MID_COMPUTE);
//				} else if (top != null && top.circularState == CircularTraceState.IDLE) {
//					// Re-evaluating circular state, do not construct a new PendingTrace
//					top.circularState = CircularTraceState.MID_COMPUTE;
//					break;
//				} else {
				tr = new PendingTrace(astNode, prop, getComputeBeginInformation(astNode, prop, args[3]), top, args);
//				}
				if (top == null) {
					completed.add(tr);
				} else {
					top.dependencies.add(tr);
				}
				active.push(tr);

//				if (top != null && top.circularState == CircularTraceState.IDLE) {
//					top.circularState = CircularTraceState.MID_COMPUTE;
//					break;
//				}
//				System.out.printf("Encode %s on %s\n", attribute, astNode.getClass().getSimpleName());
//				System.out.println(args[3]); // Params
				// TODO handle params correctly
//				final PendingTrace tr = new PendingTrace(astNode, prop,
//						getComputeBeginInformation(astNode, prop, args[3]), top, null);

//				if (nextComputeBeginIsCircular) {
//					tr.isCircular = true;
//					nextComputeBeginIsCircular = false;
//				}
				break;
			}
			case "COMPUTE_END": {
//				System.out.printf("%3d COMPUTE_END %s.%s%n", --computeDepth, args[1] + "", args[2] + "");
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
//				System.out.println(active.size() +" < " + event +" " + astNode.getClass().getSimpleName() +" | " + attribute + (active.isEmpty() ? "" : (" " + active.peek().circularState)));
				if (active.isEmpty()) {
					System.out.println("!! Got COMPUTE_END on empty stack..");
					Thread.dumpStack();
					dumpLastAcceptsInfo();
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
//					if (active.isEmpty()) {
//						System.out.println("!! Empty stack after synthesizing COMPUTE_END events..");
//						Thread.dumpStack();
//						System.out.println("Received a total of " + totalNumAccepts + " events to this trace receiver");
//						dumpLastAcceptsInfo();
//
//						System.exit(1);
//					}
					popped = active.pop();
				}
//				if (popped.circularState != null && popped.circularState == CircularTraceState.MID_COMPUTE) {
//					// Not ended yet, wait until CIRCULAR_CASEX_RETURN
////					System.out.println("..idle");
//					popped.circularState = CircularTraceState.IDLE;
//					active.push(popped);
//					break;
//				}
				final Object value = args[4];
				if (popped.preTraceValue != null) {
					onComputeEnd(value, popped.preTraceValue);
				}
				popped.value = value;
				break;
			}

//			case "CIRCULAR_CASE1_START": // Fall-through
////			case "CIRCULAR_CASE2_START": // Fall-through
////			case "CIRCULAR_CASE3_START":
//			{
//				nextComputeIsCircular = true;
//				circularIgnoreDepth++;
////				System.out.println(event +" " + args[2]);
//				break;
//			}
//			case "CIRCULAR_CASE1_RETURN": // Fall-through
////			case "CIRCULAR_CASE2_RETURN": // Fall-through
////			case "CIRCULAR_CASE3_RETURN":
//			{
//				// Expected structure: same as COMPUTE_BEGIN
//				final Object astNode = args[1];
//				final String attribute = String.valueOf(args[2]);
//				if (excludeAttribute(astNode, attribute)) {
//					return;
//				}
////				System.out.println(event +" " + args[2]);
//				final Object value = args[4];
//				final PendingTrace popped = active.pop();
//				if (popped.circularState != CircularTraceState.IDLE) {
//					System.err.println("??? Wrong circular state popped: " + popped.circularState);
//					System.exit(1);
//				}
//				if (popped.preTraceValue != null) {
//					onComputeEnd(value, popped.preTraceValue);
//				}
//				popped.value = args[4];
//				break;
//			}
			default: {
//				System.err.printf("Unknown tracing event '%s'%n", event);
				break;
			}
			}
		} finally {
			recursionProtection = false;
		}

	}

	protected void recordAcceptedArgs(Object[] args) {
		lastAcceptRingBuffer[lastAcceptRingBufferNextPos] = args;
		lastAcceptRingBufferNextPos = (lastAcceptRingBufferNextPos + 1) % lastAcceptRingBuffer.length;
		lastAcceptRingBufferLength = Math.min(lastAcceptRingBufferLength + 1, lastAcceptRingBuffer.length);
		++totalNumAccepts;
	}

	private void dumpLastAcceptsInfo() {
		System.out.println("Most recent callbacks to this tracingBuilder..");
		int computeBalance = 0;
		int circleBalance = 0;
		for (int i = 0; i < lastAcceptRingBufferLength; ++i) {
			final int pos = (lastAcceptRingBufferNextPos - 1 - i + lastAcceptRingBuffer.length)
					% lastAcceptRingBuffer.length;
			final Object[] recentArgs = lastAcceptRingBuffer[pos];
//			String infoTail = "";
			switch (String.valueOf(recentArgs[0])) {
			case "COMPUTE_BEGIN": {
				++computeBalance;
				break;
			}
			case "COMPUTE_END": {
				++computeBalance;
				break;
			}
			case "CIRCULAR_CASE1_START": {
				++circleBalance;
				break;
			}
			case "CIRCULAR_CASE1_RETURN": {
				--circleBalance;
				break;
			}
			}
//			if ("COMPUTE_BEGIN".equals(String.valueOf(recentArgs[0]))) {
//				++computeBalance;
//			} else if ("COMPUTE_END".equals(String.valueOf(recentArgs[0]))) {
//				--computeBalance;
//			} else {
//				infoTail = "     <- Unknown trace event kind";
//			}
			String blockSign = circleBalance < 0 ? "# |" : "   ";
			System.out.printf("#%4d | %4d | %s%s%n", i, computeBalance, blockSign, Arrays.toString(recentArgs));
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
