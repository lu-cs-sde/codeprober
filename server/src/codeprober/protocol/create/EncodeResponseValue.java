package codeprober.protocol.create;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.CreateLocator;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.data.Diagnostic;
import codeprober.protocol.data.HighlightableMessage;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.util.MagicStdoutMessageParser;

public class EncodeResponseValue {
	// Settings and callbacks which may be set by tools using CodeProber as a library
	// CodeProber do notwrite to these manually, so feel free to overwrite
	public static Consumer<AstNode> faultyNodeLocatorInspector = null;
	public static boolean shouldSortSetAndMapContents = false;
	public static BiConsumer<Object, List<RpcBodyLine>> defaultToStringOverride = null;
	public static BiConsumer<Object, List<RpcBodyLine>> failedCreatingLocatorOverride = null;

	private static final RpcBodyLine nullLine = RpcBodyLine.fromPlain("null");

	public static boolean shouldExpandListNodes = true;

	public static void encodeTyped(AstInfo info, List<RpcBodyLine> out, List<Diagnostic> diagnostics, Object value,
			HashSet<Object> alreadyVisitedNodes) {
		if (value == null) {
			out.add(nullLine);
			return;
		}

		try {
			final Object diagnosticValue = Reflect.invoke0(value, "cpr_getDiagnostic");
			if (diagnosticValue != null) {
				if (diagnosticValue instanceof String) {
					final String diagStr = (String) diagnosticValue;
					if (diagStr.startsWith("HLHOVER@")) {
						final Matcher matcher = Pattern.compile("HLHOVER@(\\d+);(\\d+)").matcher(diagStr);
						if (!matcher.matches()) {
							System.err.println("Invalid HLHOVER string '" + diagnosticValue + "'");
						} else {
							final int start = Integer.parseInt(matcher.group(1));
							final int end = Integer.parseInt(matcher.group(2));
							boolean addToString = true;
							try {
								Object preferredView = Reflect.invoke0(value, "cpr_getOutput");
								out.add(RpcBodyLine.fromHighlightMsg(
										new HighlightableMessage(start, end, preferredView.toString())));
								addToString = false;
								return;
							} catch (InvokeProblem e) {
								// Fall down to default view
							}
							if (addToString) {
								out.add(RpcBodyLine
										.fromHighlightMsg(new HighlightableMessage(start, end, value.toString())));
							}
						}
					} else {

//					if (((String) diagnosticValue).startsWith("))
						final Diagnostic d = MagicStdoutMessageParser.parse(diagStr);
						if (d != null) {
							if (diagnostics != null) {
								diagnostics.add(d);
							}
						} else {
							System.err.println("Invalid diagnostic string '" + diagnosticValue + "'");
						}
					}
				} else {
					System.err.println("Unknown cpr_getDiagnostic return type. Expected String, got "
							+ diagnosticValue.getClass().getName());
				}
			}
		} catch (InvokeProblem e) {
			// OK, this is an optional attribute after all
		}

		// Clone to avoid showing 'already visited' when this encoding 'branch' hasn't
		// visited it.
		alreadyVisitedNodes = new HashSet<Object>(alreadyVisitedNodes);

		if (info.baseAstClazz.isInstance(value)) {
			value = new AstNode(value);
		}

		if (value instanceof AstNode) {
			AstNode node = (AstNode) value;

			if (!alreadyVisitedNodes.contains(node.underlyingAstNode)) {
				try {
					Object preferredView = Reflect.invoke0(node.underlyingAstNode, "cpr_getOutput");
					alreadyVisitedNodes.add(node.underlyingAstNode);
					encodeTyped(info, out, diagnostics, preferredView, alreadyVisitedNodes);
					return;
				} catch (InvokeProblem e) {
					// Fall down to default view
				}
			}

			try {
				final NodeLocator locator = CreateLocator.fromNode(info, node);
				if (locator != null) {
					out.add(RpcBodyLine.fromNode(locator));

					out.add(RpcBodyLine.fromPlain("\n"));
					if (shouldExpandListNodes && node.isList() && !alreadyVisitedNodes.contains(node.underlyingAstNode)) {
						final int numEntries = node.getNumChildren(info);
						out.add(RpcBodyLine.fromPlain(""));
						if (numEntries == 0) {
							out.add(RpcBodyLine.fromPlain("<empty list>"));
						} else {
							alreadyVisitedNodes.add(node.underlyingAstNode);
							out.add(RpcBodyLine.fromPlain("List contents [" + numEntries + "]:"));
							for (AstNode child : node.getChildren(info)) {
								encodeTyped(info, out, diagnostics, child, alreadyVisitedNodes);
							}
						}
						return;
					}
				} else {
					if (faultyNodeLocatorInspector != null) {
						faultyNodeLocatorInspector.accept(node);
					}

					if (failedCreatingLocatorOverride != null) {
						failedCreatingLocatorOverride.accept(node.underlyingAstNode, out);
					} else {
						out.add(RpcBodyLine.fromPlain("Couldn't create locator for " + node.underlyingAstNode));
						out.add(RpcBodyLine.fromPlain(
								"This could indicate a caching issue, where a detached AST node is stored "));
						out.add(RpcBodyLine
								.fromPlain("somewhere even after a re-parse or flushTreeCache() is called."));
						out.add(RpcBodyLine.fromPlain("Try setting the 'AST caching strategy' to 'None' or 'Purge'."));
						out.add(RpcBodyLine.fromPlain(
								"If that helps, then you maybe have a caching problem somewhere in the AST."));
						out.add(RpcBodyLine.fromPlain(
								"If that doesn't help, then please look at any error messages in the terminal where you started codeprober.jar."));
						out.add(RpcBodyLine.fromPlain(
								"If that doesn't help either, then you may have found a bug. Please report it!"));
					}
				}
				return;
			} catch (InvokeProblem e) {
				System.err.println("Failed creating locator to " + node);
				e.printStackTrace();
				// Fall down to default toString encoding below
			}
		} else {
			if (!alreadyVisitedNodes.contains(value)) {
				try {
					Object preferredView = Reflect.invoke0(value, "cpr_getOutput");
					alreadyVisitedNodes.add(value);
					encodeTyped(info, out, diagnostics, preferredView, alreadyVisitedNodes);
					return;
				} catch (InvokeProblem e) {
					// Fall down to default view
				}
			}
		}

		if (value instanceof Iterable<?>) {
			if (alreadyVisitedNodes.contains(value)) {
				out.add(RpcBodyLine.fromPlain("<< reference loop to already visited value " + value + " >>"));
				return;
			}
			alreadyVisitedNodes.add(value);

			List<RpcBodyLine> indent = new ArrayList<>();
			Iterable<?> iter = (Iterable<?>) value;
			for (Object o : iter) {
				encodeTyped(info, indent, diagnostics, o, alreadyVisitedNodes);
			}
			if (shouldSortSetAndMapContents && indent.size() > 1) {
				// Cheating a bit here. Checking "instanceof Set" is good, checking if the name
				// contains "Set" is a hack.
				// This is mainly to support the "SmallSet" class in ExtendJ.
				// TODO figure out a cleaner way to detect unordered iterables.
				// We could consider checking "!(value instanceof List<?>)", but there may be
				// non-List iterables where order is significant. So the current check, while
				// hacky, is a little more conservative
				final boolean isUnorderedIterable = value instanceof Set<?>
						|| value.getClass().getSimpleName().contains("Set");
				if (isUnorderedIterable) {
					indent = indent.stream() //
							.map(x -> new LineSorterHelper(x)) //
							.sorted() //
							.map(x -> x.line)
							.collect(Collectors.toList());
				}
			}
			out.add(RpcBodyLine.fromArr(indent));
			return;
		}
		if (value instanceof Iterator<?>) {
			if (alreadyVisitedNodes.contains(value)) {
				out.add(RpcBodyLine.fromPlain("<< reference loop to already visited value " + value + " >>"));
				return;
			}
			alreadyVisitedNodes.add(value);
			final List<RpcBodyLine> indent = new ArrayList<>();
			Iterator<?> iter = (Iterator<?>) value;
			while (iter.hasNext()) {
				encodeTyped(info, indent, diagnostics, iter.next(), alreadyVisitedNodes);
			}
			out.add(RpcBodyLine.fromArr(indent));
			return;
		}
		if (shouldSortSetAndMapContents && value instanceof Map<?, ?>) {
			if (alreadyVisitedNodes.contains(value)) {
				out.add(RpcBodyLine.fromPlain("<< reference loop to already visited value " + value + " >>"));
				return;
			}
			alreadyVisitedNodes.add(value);

			List<RpcBodyLine> indent = new ArrayList<>();
			for (Entry<?, ?> o : ((Map<?, ?>) value).entrySet()) {
				// Version 1: encode the entire entry
				// encodeTyped(info, indent, diagnostics, o, alreadyVisitedNodes);
				// Version 2: Encode key/value separately
				final List<RpcBodyLine> indenterer = new ArrayList<>();
				encodeTyped(info, indenterer, diagnostics, o.getKey(), alreadyVisitedNodes);
				indenterer.add(RpcBodyLine.fromPlain(" = "));
				encodeTyped(info, indenterer, diagnostics, o.getValue(), alreadyVisitedNodes);
				indent.add(RpcBodyLine.fromArr(indenterer));
			}
			if (indent.size() > 1) {
				indent = indent.stream().map(x -> new LineSorterHelper(x)).sorted().map(x -> x.line)
						.collect(Collectors.toList());
			}
			out.add(RpcBodyLine.fromArr(indent));
			return;
		}
		if (value instanceof Object[]) {
			if (alreadyVisitedNodes.contains(value)) {
				out.add(RpcBodyLine.fromPlain("<< reference loop to already visited value " + value + " >>"));
				return;
			}
			alreadyVisitedNodes.add(value);

			final List<RpcBodyLine> indent = new ArrayList<>();
			for (Object child : (Object[]) value) {
				encodeTyped(info, indent, diagnostics, child, alreadyVisitedNodes);
			}
			out.add(RpcBodyLine.fromArr(indent));
			return;
		}
		if (!(value instanceof String)) {
			try {
				if (value.getClass().getMethod("toString").getDeclaringClass() == Object.class) {
					if (defaultToStringOverride != null) {
						defaultToStringOverride.accept(value, out);
						return;
					}
//				if (value.getClass().isEnum()) {
//					out.put(value.toString());
//				}
					out.add(RpcBodyLine.fromPlain(
							"No toString() or cpr_getOutput() implementation in " + value.getClass().getName()));
				}
			} catch (NoSuchMethodException e) {
				System.err.println("No toString implementation for " + value.getClass());
				e.printStackTrace();
			}
		}
		for (String line : String.valueOf(value).split("\n")) {
			out.add(RpcBodyLine.fromPlain(line));
		}
	}

	private static class LineSorterHelper implements Comparable<LineSorterHelper> {
		public final RpcBodyLine line;
		private byte[] sortArr;

		public LineSorterHelper(RpcBodyLine line) {
			this.line = line;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);
			try {
				line.writeTo(dos);
			} catch (IOException impossible) {
				System.out.println("Impossible exception happened");
				impossible.printStackTrace();
				System.exit(1);
			}
			sortArr = baos.toByteArray();
		}

		@Override
		public int compareTo(LineSorterHelper other) {
			final int ourLen = sortArr.length;
			final byte[] otherArr = other.sortArr;
			final int otherLen = otherArr.length;
			if (ourLen != otherLen) {
				return ourLen < otherLen ? -1 : 1;
			}

			for (int i = 0; i < ourLen; ++i) {
				final byte ourVal = sortArr[i];
				final byte otherVal = otherArr[i];
				if (ourVal != otherVal) {
					return ourVal < otherVal ? -1 : 1;
				}
			}
			return 0;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(sortArr);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof LineSorterHelper && Arrays.equals(sortArr, ((LineSorterHelper)obj).sortArr);
		}
	}
}
