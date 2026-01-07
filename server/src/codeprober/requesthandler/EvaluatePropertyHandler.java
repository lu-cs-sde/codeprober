package codeprober.requesthandler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.locator.ApplyLocator;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.locator.CreateLocator;
import codeprober.locator.CreateLocator.LocatorMergeMethod;
import codeprober.locator.NodesWithProperty;
import codeprober.locator.PrettyPrintTree;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.metaprogramming.StreamInterceptor;
import codeprober.protocol.create.EncodeResponseValue;
import codeprober.protocol.data.Diagnostic;
import codeprober.protocol.data.EvaluatePropertyReq;
import codeprober.protocol.data.EvaluatePropertyRes;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.NullableNodeLocator;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.PropertyArgCollection;
import codeprober.protocol.data.PropertyEvaluationResult;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.protocol.data.SynchronousEvaluationResult;
import codeprober.protocol.data.Tracing;
import codeprober.requesthandler.LazyParser.ParsedAst;
import codeprober.util.BenchmarkTimer;
import codeprober.util.MagicStdoutMessageParser;

public class EvaluatePropertyHandler {

	public static class UnpackedAttrValue {
		public final Object unpacked;
		public final PropertyArg response;

		public UnpackedAttrValue(Object unpacked, PropertyArg response) {
			this.unpacked = unpacked;
			this.response = response;
		}
	}

	public static UnpackedAttrValue unpackAttrValue(AstInfo info, PropertyArg val, Consumer<String> streamMsgReceiver) {
		switch (val.type) {
		case integer:
		case string:
		case bool:
			return new UnpackedAttrValue(val.value, val);

		case nodeLocator: {
			final NullableNodeLocator nl = val.asNodeLocator();
			if (nl.value == null) {
				return new UnpackedAttrValue(null, val);
			}
			final ResolvedNode res = ApplyLocator.toNode(info, nl.value);
			if (res == null) {
				System.out.println("Failed locating node from argument");
				return null;
			}
			return new UnpackedAttrValue(res.node.underlyingAstNode,
					PropertyArg.fromNodeLocator(new NullableNodeLocator(nl.type, res.nodeLocator)));
		}

		case collection: {
			final PropertyArgCollection coll = val.asCollection();

			final List<UnpackedAttrValue> ret = new ArrayList<>();
			for (PropertyArg sub : coll.entries) {
				ret.add(unpackAttrValue(info, sub, streamMsgReceiver));
			}
			Collector<Object, ?, ? extends Collection<?>> collector;
			switch (coll.type) {
			case "java.util.Set":
				collector = Collectors.toSet();
				break;
			default:
				collector = Collectors.toList();
			}
			return new UnpackedAttrValue( //
					ret.stream().map(x -> x.unpacked).collect(collector),
					PropertyArg.fromCollection(new PropertyArgCollection(coll.type,
							ret.stream().map(x -> x.response).collect(Collectors.toList()))));
		}

		case outputstream: {
			final StreamInterceptor si = new StreamInterceptor(new PrintStream(new ByteArrayOutputStream())) {

				@Override
				protected void onLine(String line) {
					streamMsgReceiver.accept(line);
				}
			};
			return new UnpackedAttrValue(si, val);
		}

		case any: {
			final PropertyArg any = val.asAny();
			final UnpackedAttrValue unpacked = unpackAttrValue(info, any, streamMsgReceiver);
			return new UnpackedAttrValue(unpacked, PropertyArg.fromAny(any));
		}

		default:
			throw new RuntimeException("Unknown attr value type " + val.type);
		}

	}

	public static Class<?> getValueType(AstInfo info, PropertyArg arg) {
		switch (arg.type) {
		case bool: {
			return Boolean.TYPE;
		}
		case integer: {
			return Integer.TYPE;
		}
		case string: {
			return String.class;
		}
		case collection: {
			return info.loadAstClass.apply(arg.asCollection().type);
		}
		case nodeLocator: {
			return info.loadAstClass.apply(arg.asNodeLocator().type);
		}
		case outputstream: {
			return info.loadAstClass.apply(arg.asOutputstream());
		}
		case any: {
			return Object.class;
		}
		default: {
			throw new RuntimeException("Unsupported arg type '" + arg.type + "'");
		}

		}
	}

	public static EvaluatePropertyRes apply(EvaluatePropertyReq req, LazyParser parser) {
		BenchmarkTimer.resetAll();

		final long requestStart = System.nanoTime();
		final ParsedAst parsed = parser.parse(req.src);

//		final long parseTime = System.nanoTime() - requestTime;

		final List<Diagnostic> diagnostics = new ArrayList<>();
		final List<RpcBodyLine> body = new ArrayList<>();
		final AtomicReference<NodeLocator> newLocator = new AtomicReference<>(null);
		final AtomicReference<List<PropertyArg>> updatedArgsPtr = new AtomicReference<>();
		if (parsed.info == null) {
			if (parsed.captures != null) {
				for (RpcBodyLine line : parsed.captures) {
					body.add(line);

					switch (line.type) {
					case stdout: {
						final Diagnostic diagnostic = MagicStdoutMessageParser.parse(line.asStdout());
						if (diagnostic != null) {
							diagnostics.add(diagnostic);
						}
						break;
					}

					case stderr:
						final Diagnostic diagnostic = MagicStdoutMessageParser.parse(line.asStderr());
						if (diagnostic != null) {
							diagnostics.add(diagnostic);
						}
						break;

					default:
						break;
					}
				}
			}

//			return new EvaluatePropertyRes(PropertyEvaluationResult.fromSync(new SynchronousEvaluationResult(parsed.captures, 0, 0, 0, 0, 0, null, null, null)))
		} else {
			final TracingBuilder traceBuilder = new TracingBuilder(parsed.info);
			final AtomicBoolean ignoreStdio = new AtomicBoolean(false);
			final Runnable evaluateAttr = () -> {
				boolean shouldExpandListNodes = req.flattenForTextProbes == null || !req.flattenForTextProbes;
				if (req.captureTraces != null && req.captureTraces.booleanValue()) {
					if (parsed.info.hasOverride1(parsed.info.ast.underlyingAstNode.getClass(), "cpr_setTraceReceiver",
							Consumer.class)) {
						if (req.flushBeforeTraceCollection != null && req.flushBeforeTraceCollection.booleanValue()) {
							if (parsed.info.hasOverride0(parsed.info.ast.underlyingAstNode.getClass(),
									"flushTreeCache")) {
								try {
									Reflect.invoke0(parsed.info.ast.underlyingAstNode, "flushTreeCache");
								} catch (InvokeProblem e) {
									System.out.println("Problem flushing tree during trace setup");
									e.printStackTrace(System.out);
								}
							}
						}
						Reflect.invokeN(parsed.info.ast.underlyingAstNode, "cpr_setTraceReceiver",
								new Class<?>[] { Consumer.class }, new Object[] { traceBuilder });

					} else {
						body.add(RpcBodyLine.fromStderr(
								"Asked to collect trace information, but cpr_setTraceReceiver is not implemented."));
						body.add(RpcBodyLine.fromStderr("See CodeProber documentation for more information."));
					}

				}
				final ResolvedNode match;
				ignoreStdio.set(true);
				try {
					match = ApplyLocator.toNode(parsed.info, req.locator,
							req.skipResultLocator == null ? true : !req.skipResultLocator);
				} finally {
					ignoreStdio.set(false);
				}
				if (match == null) {
					body.add(RpcBodyLine
							.fromPlain("No matching node found\n\nTry remaking the probe\nat a different line/column"));
					return;
				}
				newLocator.set(match.nodeLocator);

//				if (parsed.info.tracingRegistration != null) {
//					parsed.info.tracingRegistration.accept(traceBuilder);
//				}

				try {
//					final JSONArray args = ProbeProtocol.Attribute.args.get(queryAttr, null);
					final Object value;

					// Respond with new args, just like we respond with a new locator
//					JSONArray updatedArgs = new JSONArray();

					final String queryAttrName = req.property.name;
					if (queryAttrName.startsWith("l:")) {
						// Special labeled zero-arg attr invocation
						BenchmarkTimer.EVALUATE_ATTR.enter();
						try {
							value = Reflect.invokeN(match.node.underlyingAstNode, "cpr_lInvoke",
									new Class[] { String.class }, new Object[] { queryAttrName.substring(2) });
						} finally {
							BenchmarkTimer.EVALUATE_ATTR.exit();
						}
					} else if (queryAttrName.startsWith("m:")) {
						// Meta-attr, not actually in target AST
						switch (queryAttrName) {
						case "m:NodesWithProperty": {
							if (req.property.args == null || req.property.args.size() == 0) {
								throw new IllegalArgumentException(
										"Need at least one argument for m:NodesWithProperty");
							}
							int limit = 100;
							final String limitStr = System.getenv("QUERY_PROBE_OUTPUT_LIMIT");
							if (limitStr != null) {
								try {
									limit = Integer.parseInt(limitStr);
								} catch (NumberFormatException e) {
									System.err.println("Invalid value for QUERY_PROBE_OUTPUT_LIMIT");
									e.printStackTrace();
								}
							}
							final String propName = req.property.args.get(0).asString();
							String predicate = null;
							if (req.property.args.size() >= 2) {
								predicate = req.property.args.get(1).asString();
							}

							shouldExpandListNodes = false;
							value = NodesWithProperty.get(parsed.info, match.node, propName, predicate, limit);
							break;
						}
						case "m:PrettyPrint": {
							final LocatorMergeMethod restoreMethod = CreateLocator.getMergeMethod();
							CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
							try {
								value = PrettyPrintTree.prettyPrint(parsed.info, match.node);
							} finally {
								CreateLocator.setMergeMethod(restoreMethod);
							}
							break;
						}
						case "m:AttrChain": {
							// A list of 1 or more attribute names to be evaluated in sequence
							Object chainVal = match.node.underlyingAstNode;
							if (req.property.args != null) {
								chainVal = ListPropertiesHandler.evaluateAttrChain(parsed.info, chainVal,
										req.property.args.stream().map(arg -> {
											if (!arg.isString()) {
												throw new IllegalArgumentException(
														"All arguments to m:AttrChain must be strings, got "
																+ arg.type);
											}
											return arg.asString();
										}).collect(Collectors.toList()), req.attrChainArgs, body);

								if (chainVal == ListPropertiesHandler.ATTR_CHAIN_FAILED) {
									if (!req.captureStdout && shouldExpandListNodes) {
										body.add(RpcBodyLine.fromPlain(
												"Attribute evaluation chain failed, click 'Capture stdout' to see full error."));
									}
									return;
								}
							}
							value = chainVal;
							break;
						}
						default: {
							value = "Invalid meta-attribute '" + queryAttrName + "'";
							break;
						}
						}
					} else if (req.property.args == null || req.property.args.isEmpty()) {
						BenchmarkTimer.EVALUATE_ATTR.enter();
						try {
							value = Reflect.invoke0(match.node.underlyingAstNode, queryAttrName);
						} finally {
							BenchmarkTimer.EVALUATE_ATTR.exit();
						}
					} else {
						final int numArgs = req.property.args.size();
						final Class<?>[] argTypes = new Class<?>[numArgs];
						final Object[] argValues = new Object[numArgs];
						final List<PropertyArg> updatedArgs = new ArrayList<>();
						updatedArgsPtr.set(updatedArgs);
						for (int i = 0; i < numArgs; ++i) {
							final PropertyArg arg = req.property.args.get(i);
							final Class<?> argType = getValueType(parsed.info, arg);
							argTypes[i] = argType;
							final UnpackedAttrValue unpacked = unpackAttrValue(parsed.info, arg,
									msg -> body.add(RpcBodyLine.fromStreamArg(msg)));
							argValues[i] = unpacked.unpacked;
							updatedArgs.add(unpacked.response);
						}
						BenchmarkTimer.EVALUATE_ATTR.enter();
						try {
							value = Reflect.invokeN(match.node.underlyingAstNode, queryAttrName, argTypes, argValues);
						} finally {
							BenchmarkTimer.EVALUATE_ATTR.exit();
						}
						for (Object argValue : argValues) {
							// Flush all StreamInterceptor args
							if (argValue instanceof StreamInterceptor) {
								((StreamInterceptor) argValue).consume();
							}
						}
					}

					traceBuilder.stop();

					if (value != Reflect.VOID_RETURN_VALUE) {
						CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
						try {
							if (value instanceof String && ((String) value).startsWith("@@DOT:")) {
								// Hacky dot support
								body.add(RpcBodyLine.fromDotGraph(((String) value).substring("@@DOT:".length())));
							} else if (value instanceof String && ((String) value).startsWith("@@HTML:")) {
								// Hacky html support
								body.add(RpcBodyLine.fromHtml(((String) value).substring("@@HTML:".length())));
							} else if (value instanceof RpcBodyLine) {
								// Already correct type
								body.add((RpcBodyLine) value);
							} else {

								try {
									EncodeResponseValue.shouldExpandListNodes = shouldExpandListNodes;
									EncodeResponseValue.encodeTyped(parsed.info, body, diagnostics, value,
											new HashSet<>());

//									for (RpcBodyLine line : body) {
//										System.out.println(body.size());
////										System.out.println("> " + line.type);
//									}
//									System.out.println("-..-");
//									for (RpcBodyLine line : body) {
//										System.out.println("> " + line.toJSON());
//									}
//									System.out.println("--");
//									if (!shouldExpandListNodes && maybeInlineArrays) {
//										// Inline arrays
//										for (int i = 0; i < body.size(); ++i) {
//											final RpcBodyLine row = body.get(i);
//											if (row.isArr()) {
//												body.remove(i);
//												body.addAll(i, row.asArr());
//												i += row.asArr().size() - 1;
//											}
//										}
//									}
								} finally {
									EncodeResponseValue.shouldExpandListNodes = true;
								}
							}
						} finally {
							CreateLocator.setMergeMethod(LocatorMergeMethod.DEFAULT_METHOD);
						}
					}
					final Tracing trace = traceBuilder.finish(match.nodeLocator);
					if (trace != null) {
						body.add(RpcBodyLine.fromTracing(trace));
					}

//					ProbeProtocol.Attribute.args.put(retBuilder, updatedArgs);
				} catch (InvokeProblem e) {
					Throwable cause = e.getCause();
					if (cause instanceof NoSuchMethodException) {
						body.add(RpcBodyLine.fromPlain(String.format("No such attribute '%s' on %s", req.property.name,
								match.node.underlyingAstNode.getClass().getName())));
					} else {
						// Some AST implementations may assume that after throwing an exception, the
						// current process stops.
						// This is normal for many cli tools, and therefore there is no need to "clean
						// up" any partially invalid state.
						// This is the case, for example, for the JastAdd circularity state.
						// Since the cached AST may be invalid, we must discard it
						parser.discardCachedAst();

						if (cause instanceof InvocationTargetException && cause.getCause() != null) {
							cause = cause.getCause();
						}
						if (cause != null && cause.getCause() != null) {
							cause.getCause().printStackTrace();
						} else {
							(cause != null ? cause : e).printStackTrace();
						}
						body.add(RpcBodyLine.fromPlain("Exception thrown while evaluating attribute."));
						if (!req.captureStdout) {
							body.add(RpcBodyLine.fromPlain("Click 'Capture stdout' to see full error."));
						}
					}
					traceBuilder.stop();
				}
			};

			diagnostics.addAll(
					StdIoInterceptor.performCaptured((stdout, line) -> MagicStdoutMessageParser.parse(line), () -> {
						try {
							if (req.captureStdout) {
								// TODO add messages live instead of after everything finishes
								final BiFunction<Boolean, String, RpcBodyLine> encoder = StdIoInterceptor
										.createDefaultLineEncoder();
								StdIoInterceptor.performLiveCaptured((stdout, line) -> {
									if (ignoreStdio.get()) {
										return;
									}
									body.add(encoder.apply(stdout, line));
								}, () -> evaluateAttr.run());
							} else {
								evaluateAttr.run();
							}
						} catch (RuntimeException e) {
							if (e.getCause() instanceof ClassNotFoundException) {
								final ClassNotFoundException cce = (ClassNotFoundException) e.getCause();
//						System.err.println();
								body.add(RpcBodyLine.fromPlain("Bad type reference: " + cce.getMessage()));
								body.add(RpcBodyLine.fromPlain(
										"Either an AST node type definition was removed, or this probe may have been created for another tool"));
								return;
							}
							throw e;
						} finally {
							// Stop here too, just in case the other stop doesn't execute.
							// A little scared about permanent trace receiver that fills memory.
							traceBuilder.stop();
						}
					}));
//			final List<RpcBodyLine> caps = StdIoInterceptor
//					.performDefaultCapture(() -> handleParsedAst(res.rootNode, queryObj, retBuilder, bodyBuilder));
//
//			for (RpcBodyLine line : caps) {
//				errors.put(line.toJSON());
//			}
		}

		return new EvaluatePropertyRes(PropertyEvaluationResult.fromSync(new SynchronousEvaluationResult( //
				body, //
				System.nanoTime() - requestStart, // totalTime
				parsed.parseTimeNanos, //
				BenchmarkTimer.CREATE_LOCATOR.getAccumulatedNano(), //
				BenchmarkTimer.APPLY_LOCATOR.getAccumulatedNano(), //
				BenchmarkTimer.EVALUATE_ATTR.getAccumulatedNano(), //
				BenchmarkTimer.LIST_NODES.getAccumulatedNano(), //
				BenchmarkTimer.LIST_PROPERTIES.getAccumulatedNano(), //
				diagnostics, //
				updatedArgsPtr.get(), //
				newLocator.get() //
		)));
	}
}
