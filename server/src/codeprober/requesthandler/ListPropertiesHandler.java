package codeprober.requesthandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import codeprober.AstInfo;
import codeprober.locator.ApplyLocator;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.locator.AttrsInNode;
import codeprober.locator.AttrsInNode.BaseInclusionFilter;
import codeprober.protocol.create.CreateType;
import codeprober.protocol.data.ListPropertiesReq;
import codeprober.protocol.data.ListPropertiesRes;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
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
		return new ListPropertiesRes(parsed.captures,
				AttrsInNode.getTyped(parsed.info, match.node, AttrsInNode.extractFilter(parsed.info, match.node),
						req.all //
								? BaseInclusionFilter.ALMOST_ALL_NORMAL_METHODS
								: BaseInclusionFilter.ATTRIBUTES_ONLY));
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
			final String name = m.getName();
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
		final Set<String> deduplicator = new HashSet<>();
		methods.removeIf(x -> {
			if (deduplicator.add(x.toJSON().toString())) {
				return false;
			}
			return true;
		});
		return methods;
	}

	public static final Object ATTR_CHAIN_FAILED = new Object();

}
