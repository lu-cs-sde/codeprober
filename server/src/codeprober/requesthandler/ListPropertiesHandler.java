package codeprober.requesthandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.ApplyLocator;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.locator.AttrsInNode;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.create.CreateType;
import codeprober.protocol.data.ListPropertiesReq;
import codeprober.protocol.data.ListPropertiesRes;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.requesthandler.EvaluatePropertyHandler.UnpackedAttrValue;
import codeprober.requesthandler.LazyParser.ParsedAst;

public class ListPropertiesHandler {

	public static ListPropertiesRes apply(ListPropertiesReq req, LazyParser parser) {
		final ParsedAst parsed = parser.parse(req.src);
		if (parsed.info == null) {
			return new ListPropertiesRes(parsed.captures, null);
		}
		final ResolvedNode match = ApplyLocator.toNode(parsed.info, req.locator);
		if (match == null) {
			return new ListPropertiesRes(parsed.captures, null);
		}
		if (req.attrChain == null) {
			return new ListPropertiesRes(parsed.captures, AttrsInNode.getTyped(parsed.info, match.node,
					AttrsInNode.extractFilter(parsed.info, match.node), req.all));
		}

		final List<RpcBodyLine> body = new ArrayList<>();
		body.addAll(parsed.captures);
		Object chainVal = evaluateAttrChain(parsed.info, match.node.underlyingAstNode, req.attrChain, null, body);
		if (chainVal == ATTR_CHAIN_FAILED || chainVal == null) {
			return new ListPropertiesRes(body);
		}
		if (parsed.info.baseAstClazz.isInstance(chainVal)) {
			return new ListPropertiesRes(body, AttrsInNode.getTyped(parsed.info, new AstNode(chainVal),
					AttrsInNode.extractFilter(parsed.info, match.node), req.all));
		}
		// Else, plain java object. List methods with zero arguments that return
		// something
		List<Property> methods = extractPropertiesFromNonAstNode(parsed.info, chainVal);
		return new ListPropertiesRes(body, methods);
	}

	public static List<Property> extractPropertiesFromNonAstNode(AstInfo info, Object chainVal) {
		List<Property> methods = new ArrayList<>();
		for (Method m : chainVal.getClass().getMethods()) {
			if (m.getReturnType() == Void.TYPE) {
				continue;
			}

			final List<PropertyArg> args = CreateType.fromParameters(info, m.getParameters());
			if (args == null) {
				continue;
			}

			String name = m.getName();
			switch (name) {
			case "wait":
			case "notify":
			case "notifyAll":
			case "clone":
			case "hashCode":
				if (m.getDeclaringClass() == Object.class) {
					// Don't want this
					break;
				}
				// Else: fall through
			default:
				methods.add(new Property(name, args));
			}
		}
		return methods;
	}

	public static final Object ATTR_CHAIN_FAILED = new Object();

	public static Object evaluateAttrChain(AstInfo info, Object chainVal, List<String> attrs,
			List<List<PropertyArg>> attrArgs, List<RpcBodyLine> errBody) {
		int stepIdx = -1;
		for (String step : attrs) {
			if (chainVal == null) {
				errBody.add(RpcBodyLine.fromPlain(String.format("No such attribute '%s' on null", step)));
				return ATTR_CHAIN_FAILED;
			}
			++stepIdx;
			try {
				if (step.startsWith("l:")) {
					chainVal = Reflect.invokeN(chainVal, "cpr_lInvoke", new Class[] { String.class },
							new Object[] { step.substring("l:".length()) });
				} else if (attrArgs != null) {
					List<PropertyArg> args = attrArgs.get(stepIdx);
					final int numArgs = args.size();
					final Class<?>[] argTypes = new Class<?>[numArgs];
					final Object[] argValues = new Object[numArgs];
					for (int i = 0; i < numArgs; ++i) {
						final PropertyArg arg = args.get(i);
						final Class<?> argType = EvaluatePropertyHandler.getValueType(info, arg);
						argTypes[i] = argType;
						final UnpackedAttrValue unpacked = EvaluatePropertyHandler.unpackAttrValue(info, arg, msg -> {
							// Ignore stream messages
						});
						argValues[i] = unpacked.unpacked;
					}
					chainVal = Reflect.invokeN(chainVal, step, argTypes, argValues);
				} else {
					chainVal = Reflect.invoke0(chainVal, step);
				}
			} catch (InvokeProblem ip) {
				final Throwable cause = ip.getCause();
				if (cause instanceof NoSuchMethodException) {
					if (chainVal != null) {
						// Could be that the method exists, but with a different number of parameters
						final int actualArgCount = attrArgs != null ? attrArgs.get(stepIdx).size() : 0;
						for (Method method : chainVal.getClass().getMethods()) {
							if (!method.getName().equals(step)) {
								continue;
							}
							// Don't bother searching for all possible options, just pick the first valid
							// option
							final int formalParamCount = method.getParameterCount();
							errBody.add(RpcBodyLine.fromPlain( //
									String.format("Expected %d argument%s, got %d", //
											formalParamCount, //
											formalParamCount == 1 ? "" : "s", //
											actualArgCount)));
							return ATTR_CHAIN_FAILED;
						}
					}
					errBody.add(RpcBodyLine.fromPlain(String.format("No such attribute '%s' on %s", step,
							(chainVal == null ? "null" : chainVal.getClass().getName()))));
				} else {
					errBody.add(RpcBodyLine.fromPlain(String.format("Failed evaluating '%s' on %s", step,
							(chainVal == null ? "null" : chainVal.getClass().getName()))));

					System.err.println("Exception thrown while evaluating attribute chain:");
					ip.printStackTrace();
				}
				return ATTR_CHAIN_FAILED;
			}
		}
		return chainVal;
	}

}
