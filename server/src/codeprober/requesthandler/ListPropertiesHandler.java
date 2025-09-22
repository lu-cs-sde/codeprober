package codeprober.requesthandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import codeprober.ast.AstNode;
import codeprober.locator.ApplyLocator;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.locator.AttrsInNode;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.data.ListPropertiesReq;
import codeprober.protocol.data.ListPropertiesRes;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.RpcBodyLine;
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
		Object chainVal = evaluateAttrChain(match.node.underlyingAstNode, req.attrChain, body);
		if (chainVal == ATTR_CHAIN_FAILED || chainVal == null) {
			return new ListPropertiesRes(body);
		}
		if (parsed.info.baseAstClazz.isInstance(chainVal)) {
			return new ListPropertiesRes(body, AttrsInNode.getTyped(parsed.info, new AstNode(chainVal),
					AttrsInNode.extractFilter(parsed.info, match.node), req.all));
		}
		// Else, plain java object. List methods with zero arguments that return
		// something
		List<Property> methods = new ArrayList<>();
		for (Method m : chainVal.getClass().getMethods()) {
			if (m.getParameterCount() == 0 && m.getReturnType() != Void.TYPE) {
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
					methods.add(new Property(name));
				}
			}
		}
		return new ListPropertiesRes(body, methods);
	}

	public static final Object ATTR_CHAIN_FAILED = new Object();

	public static Object evaluateAttrChain(Object chainVal, List<String> attrs, List<RpcBodyLine> errBody) {
		for (String step : attrs) {
			try {
				if (step.startsWith("l:")) {
					chainVal = Reflect.invokeN(chainVal, "cpr_lInvoke", new Class[] { String.class },
							new Object[] { step.substring("l:".length()) });
				} else {
					chainVal = Reflect.invoke0(chainVal, step);
				}
			} catch (InvokeProblem ip) {
				final Throwable cause = ip.getCause();
				if (cause instanceof NoSuchMethodException) {
					errBody.add(RpcBodyLine.fromPlain(
							String.format("No such attribute '%s' on %s", step, chainVal.getClass().getName())));
				} else {
					errBody.add(RpcBodyLine.fromPlain(
							String.format("Failed evaluating '%s' on %s", step, chainVal.getClass().getName())));

					System.err.println("Exception thrown while evaluating attribute chain:");
					ip.printStackTrace();
				}
				return ATTR_CHAIN_FAILED;
			}
		}
		return chainVal;
	}

}
