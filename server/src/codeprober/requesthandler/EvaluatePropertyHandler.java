package codeprober.requesthandler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import codeprober.AstInfo;
import codeprober.locator.ApplyLocator;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.locator.CreateLocator;
import codeprober.locator.CreateLocator.LocatorMergeMethod;
import codeprober.locator.NodesWithProperty;
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
				return new UnpackedAttrValue(val.value, val);
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
			return new UnpackedAttrValue( //
					ret.stream().map(x -> x.unpacked).collect(Collectors.toList()),
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

//			return new EvaluatePropertyRes(PropertyEvaluationResult.fromSync(new SynchronousEvaluationResult(parsed.captures, 0, 0, 0, 0, 0, null, null, null)))
		} else {
			final Consumer<ResolvedNode> evaluateAttr = (match) -> {

				try {
//					final JSONArray args = ProbeProtocol.Attribute.args.get(queryAttr, null);
					final Object value;

					// Respond with new args, just like we respond with a new locator
//					JSONArray updatedArgs = new JSONArray();

					final String slowdown = System.getenv("SIMULATED_SLOWDOWN_MS");
					if (slowdown != null) {
						try {
							Thread.sleep(Integer.parseInt(slowdown));
						} catch (InterruptedException | NumberFormatException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

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
							if (req.property.args.size() == 0) {
								throw new IllegalArgumentException("Need at least one argument for m:NodesWithProperty");
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

							value = NodesWithProperty.get(parsed.info, match.node, propName, predicate, limit);
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
//						throw new RuntimeException("TODO support args");
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

					if (value != Reflect.VOID_RETURN_VALUE) {
						CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
						try {
							if (value instanceof String && ((String)value).startsWith("@@DOT:")) {
								// Hacky dot support
								body.add(RpcBodyLine.fromDotGraph(((String)value).substring("@@DOT:".length())));
							} else {
								EncodeResponseValue.encodeTyped(parsed.info, body, diagnostics, value, new HashSet<>());
							}
						} finally {
							CreateLocator.setMergeMethod(LocatorMergeMethod.DEFAULT_METHOD);
						}
					}
//					ProbeProtocol.Attribute.args.put(retBuilder, updatedArgs);
				} catch (InvokeProblem e) {
					Throwable cause = e.getCause();
					if (cause instanceof NoSuchMethodException) {
						body.add(RpcBodyLine.fromPlain("No such attribute '" + req.property.name + "' on "
								+ match.node.underlyingAstNode.getClass().getName()));
					} else {
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
				}
			};

			diagnostics.addAll(
					StdIoInterceptor.performCaptured((stdout, line) -> MagicStdoutMessageParser.parse(line), () -> {
						try {
							final ResolvedNode match = ApplyLocator.toNode(parsed.info, req.locator,
									req.skipResultLocator == null ? true : !req.skipResultLocator);
							if (match == null) {
								body.add(RpcBodyLine.fromPlain(
										"No matching node found\n\nTry remaking the probe\nat a different line/column"));
								return;
							}

							newLocator.set(match.nodeLocator);

							if (req.captureStdout) {
								// TODO add messages live instead of after everything finishes
								final BiFunction<Boolean, String, RpcBodyLine> encoder = StdIoInterceptor
										.createDefaultLineEncoder();
								StdIoInterceptor.performLiveCaptured((stdout, line) -> {
									body.add(encoder.apply(stdout, line));
								}, () -> evaluateAttr.accept(match));
							} else {
								evaluateAttr.accept(match);
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
