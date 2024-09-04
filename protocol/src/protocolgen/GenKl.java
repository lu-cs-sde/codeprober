package protocolgen;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.json.JSONObject;

import protocolgen.spec.Rpc;
import protocolgen.spec.Streamable;
import protocolgen.spec.StreamableUnion;

public class GenKl {

	public static void gen(List<Class<? extends Rpc>> rpcs, List<Class<? extends Streamable>> serverToClient)
			throws Exception {
		System.out.println("== GEN KL");
		final GenKl gt = new GenKl();
		for (Class<? extends Rpc> rpc : rpcs) {
			gt.genKlDef(rpc.newInstance());
		}
		for (Class<? extends Streamable> cls : serverToClient) {
			gt.requestedTypes.add(cls);
		}
		gt.drainRequests();

		StringBuilder fullFile = new StringBuilder();
		fullFile.append("#include @json/[JsonValue, JsonObject]\n");
		fullFile.append("#include @json/JsonObject/JsonObjectBuilder\n");
		final ArrayList<Entry<String, StringBuilder>> entries = new ArrayList<>(gt.target.entrySet());
		entries.sort((a, b) -> a.getKey().compareTo(b.getKey()));
		for (Entry<String, StringBuilder> ent : entries) {
			fullFile.append(ent.getValue().toString());
		}

		gt.generateParseSrc(fullFile);
		// gt.generateEncodeDst(fullFile);
		gt.generateRequetHandler(fullFile);
		// fullFile.append(String.format("\n\nexport {\n   %s\n}\n", gt.namesToExport.stream().collect(Collectors.joining("\n , "))));

		Files.write(gt.getDstFile().toPath(), fullFile.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
		System.out.println("Done");
	}

	private final Map<String, StringBuilder> target = new HashMap<>();
	// private final Set<String> namesToExport = new TreeSet<>();

	private File getDstFile() throws Exception {
		final String prop = System.getProperty("KL_DST_FILE");
		if (prop == null) {
			throw new Exception("Missing value for system property KL_DST_FILE");
		}
		final File dstFile = new File(prop);
		if (!dstFile.getParentFile().exists()) {
			throw new Exception("Bad value for system property KL_DST_FILE");
		}
		return dstFile;
	}

	private final StringBuilder parseSrcContents = new StringBuilder();
	private void generateParseSrc(StringBuilder out) {
		out.append("struct ParseSrc: JsonValue src {\n");
		out.append("  JsonObject? namedGetObj(String name): src.obj?.getObj(name) ?: fail;\n");
		out.append("  int? namedGetInt(String name): int(src.obj?.getNum(name) ?: fail);\n");
		out.append("  long? namedGetLong(String name): long(src.obj?.getNum(name) ?: fail);\n");
		out.append("  String? namedGetStr(String name): src.obj?.getStr(name);\n");
		out.append("  bool? namedGetBool(String name): src.obj?.getBool(name);\n");
		out.append("  JsonObject? getObj(): src.obj;\n");
		out.append("  int? getInt(): int(src.num ?: fail);\n");
		out.append("  long? getLong(): long(src.num ?: fail);\n");
		out.append("  String? getStr(): src.str;\n");
		out.append("  bool? getBool(): src.boolean;\n");
		out.append("  <T> List<T>? getList(.T(ParseSrc) extractor): src.arr?.mapf<T>(inner: extractor(new(inner)));\n");
		out.append("  <T> List<T>? namedGetList(String name, .T?(ParseSrc) extractor): src.obj?.getArr(name)?.mapf<T>(inner: extractor(new(inner)));\n");
		out.append(parseSrcContents.toString());
		out.append("}\n");
	}

	private class RequestHandleInfo {
		public final String[] protocolId;
		public final String userReadableName;
		public final String payloadType;
		public final String responseType;

		public RequestHandleInfo(String[] protocolId, String userReadableName, String payloadType, String responseType) {
			this.protocolId = protocolId;
			this.userReadableName = userReadableName;
			this.payloadType = payloadType;
			this.responseType = responseType;
		}
	}
	private final List<RequestHandleInfo> requestsToHandle = new ArrayList<>();

	private void generateRequetHandler(StringBuilder out) {
		out.append("worker RequestHandler {\n");
		for (RequestHandleInfo info : requestsToHandle) {
			out.append("  ." + info.responseType +"?(" + info.payloadType +")? lHandle" + info.userReadableName + "~ = null; //" + Arrays.toString(info.protocolId) + "\n");
		}
		out.append("  new();\n");
		out.append("\n");
		for (RequestHandleInfo info : requestsToHandle) {
			out.append("  void on" + info.userReadableName +"~(." + info.responseType +"?(" + info.payloadType +") newHandler) { lHandle" + info.userReadableName + " = newHandler; }\n");
		}
		out.append("\n");
		out.append("  String? handleMessage~(JsonObject obj) {\n");
		out.append("    $str = obj.getStr(\"type\") ?: fail(\"Missing 'type' in message obj\");\n");
		out.append("    switch (str) {\n");
		for (RequestHandleInfo info : requestsToHandle) {
			out.append("      ");
			for (String key : info.protocolId) {
				out.append("\"" + key +"\", ");
			}
			out.append(" {\n");
			out.append("        $handler = lHandle" + info.userReadableName +" ?: fail(\"No handler registered for " + info.userReadableName + "\"); \n");
			out.append("        return handler("+ info.payloadType+".parse(new(.obj: obj)) ?: fail(\"Failed parsing message for request " + info.userReadableName +"\"))?.json?.compactStr;\n");
			out.append("      }\n");

		}
		out.append("    }\n");

		out.append("  }\n");
		out.append("}\n");

	}

	private Output addOutputEntry(String name) {
		final StringBuilder sb = new StringBuilder();
		target.put(name, sb);
		return new Output(name, sb);
	}

	private class Output {
		public final String typeName;
		private StringBuilder sb;
		private int childCounter;
		public final List<String> parseCmds = new ArrayList<>();
		public final List<String> encodeCmds = new ArrayList<>();

		public Output(String typeName, StringBuilder sb) {
			this.typeName = typeName;
			this.sb = sb;
		}

		private Output allocAnonymousChild() {
			return addOutputEntry(typeName + (childCounter++));
		}

		public void print(String msg) {
			this.sb.append(msg);
		}

		public void println(String line) {
			print(line + "\n");
		}

		public void rewriteLastParseCmd(int preParseCmdsLen, int preEncodeCmdsLen, String fname) {
			if (parseCmds.size() != preParseCmdsLen) {
				// Added a parseable field, rewrite it to use the field name
				final String parseCmd = parseCmds.remove(preParseCmdsLen);
				String start = "namedG" + parseCmd.substring(1);
				final int lparen = start.indexOf("(");
				if (lparen == -1) {
					parseCmds.add(start + "(\"" + fname + "\")");
				} else {
					parseCmds.add(start.substring(0, lparen) + "(\"" + fname + "\", " + start.substring(lparen + 1));
				}
			}
			if (encodeCmds.size() != preEncodeCmdsLen) {
				// Added a parseable field, rewrite it to use the field name
				final String parseCmd = encodeCmds.remove(preEncodeCmdsLen);
				encodeCmds.add(parseCmd.replace("dst.set(", "dst.set(\"" + fname + "\", ").replace(" VAL", fname));
			}


		}
	}

	private void drainRequests() throws Exception {
		Set<Object> generated = new HashSet<>();

		while (generated.size() != requestedTypes.size()) {
			for (Object req : new ArrayList<>(requestedTypes)) {
				if (generated.contains(req)) {
					continue;
				}
				generated.add(req);
				if (req instanceof Class<?>) {
					Class<?> clazz = (Class<?>) req;
					if (StreamableUnion.class.isAssignableFrom(clazz)) {
						req = clazz.newInstance();
						// Fall down to 'if streamableunion' below
					} else {
						final Output out = addOutputEntry(clazz.getSimpleName());
						out.println("struct " + clazz.getSimpleName() + ":");
						// namesToExport.add(clazz.getSimpleName());
						genKlDef(out, "  ", (Streamable) clazz.newInstance());
						out.println("{");
						out.println("  static " + clazz.getSimpleName() +"? parse(ParseSrc src): new(");
						for (String cmd : out.parseCmds) {
							out.println("    src." + cmd +",");
						}
						out.println("  );");
						out.println("  JsonValue getJson(): .obj: .o(dst -> {");
						for (String cmd : out.encodeCmds) {
							out.println("    " + cmd);
						}
						out.println("    return dst;");
						out.println("  });");
						out.println("}");
						out.parseCmds.add("get" + clazz.getSimpleName());
						out.encodeCmds.add("dst.set(.obj: val.json)");
						parseSrcContents.append("  " + clazz.getSimpleName() +"? namedGet" + clazz.getSimpleName() + "(String name): .parse(new(src.obj?.get(name) ?: fail));\n");
						parseSrcContents.append("  " + clazz.getSimpleName() +"? get" + clazz.getSimpleName() + "(): .parse(this);\n");
						continue;
					}
				}

				if (req instanceof StreamableUnion) {
					StreamableUnion su = (StreamableUnion) req;
					final String sn = su.getClass().getSimpleName();
					final Output out = addOutputEntry(sn);
					out.println(String.format("union %s {", sn));
					// namesToExport.add(sn);
					for (Field f : su.getClass().getFields()) {
						f.setAccessible(true);
						final String fname = f.getName();
						out.print("  ");
						final int prePCmdsLen = out.parseCmds.size();
						// final int preECmdsLen = out.encodeCmds.size();
						genTypescriptRef(out, "", f.get(su));
						out.rewriteLastParseCmd(prePCmdsLen, out.encodeCmds.size(), "value");
						out.println(" " + fname + ";");
					}
					out.println("");
					out.println("  static " + sn +"? parse(ParseSrc src): switch (src.namedGetStr(\"type\")) {");
					int parseIdx = 0;
					for (Field f : su.getClass().getFields()) {
						f.setAccessible(true);
						final String fname = f.getName();
						out.println("    \"" + fname + "\": ." + fname + ": (src." + out.parseCmds.get(parseIdx++) + ");");
					}
					out.println("    default: fail(\"Unknown type for " + sn +"\");");
					// for (String cmd : out.parseCmds) {
					// 	out.println("    src." + cmd +",");
					// }
					out.println("  };");
					parseSrcContents.append("  " + sn +"? namedGet" + sn + "(String name): .parse(new(src.obj?.get(name) ?: fail));\n");
					parseSrcContents.append("  " + sn +"? get" + sn + "(): .parse(this);\n");

					out.println("  JsonValue getJson(): .obj: .o(dst -> {");
					out.println("    switch ($d = this) {");
					int encodeIdx = 0;
					for (Field f : su.getClass().getFields()) {
						f.setAccessible(true);
						final String fname = f.getName();
						out.println("      ." + fname + " { dst.str(\"type\", \"" + fname + "\"); " + (out.encodeCmds.size() > encodeIdx ? out.encodeCmds.get(encodeIdx++).replace("dst.set(", "dst.set(\"value\", ").replace(" VAL", " d") : "/* PANIC */") + " }");
					}
					out.println("    }");
					// for (String cmd : out.encodeCmds) {
					// 	out.println("    " + cmd);
					// }
					out.println("    return dst;");
					out.println("  });");
					out.println("}");
				} else {
					throw new Error("Bad request '" + req + "'");
				}
			}
		}
	}

	private Set<Object> requestedTypes = new LinkedHashSet<>();

	private <Arg extends Streamable, Res extends Streamable> void genKlDef(Rpc rpc) throws Exception {
		final String sn = rpc.getClass().getSimpleName();
		Output out = addOutputEntry(sn +"Req");
		out.println("struct " + sn + "Req:");
		genKlDef(out, "  ", rpc.getRequestType());
		out.println("{");
		out.println("  static " + sn  +"Req? parse(ParseSrc src): new(");
		for (String cmd : out.parseCmds) {
			out.println("    src." + cmd +",");
		}
		out.println("  );");
		out.println("}");

		out = addOutputEntry(sn + "Res");
		out.println("struct " + sn + "Res:");
		genKlDef(out, "  ", rpc.getResponseType());
		out.println("{");
		out.println("  JsonValue getJson(): .obj: .o(dst -> {");
		for (String cmd : out.encodeCmds) {
			out.println("    " + cmd);
		}
		out.println("    return dst;");
		out.println("  });");
		out.println("}");


		final Streamable reqType = rpc.getRequestType();
		Field protoField = reqType.getClass().getDeclaredField("type");
		protoField.setAccessible(true);
		final Object protoFieldVal = protoField.get(reqType);
		final String[] protoFieldEnum;
		if (protoFieldVal instanceof String) {
			protoFieldEnum = new String[]{ protoFieldVal + "" };
		} else {
			// Enum-like, like ListTree
			protoFieldEnum = (String[])protoFieldVal;
		}
		requestsToHandle.add(new RequestHandleInfo(
			protoFieldEnum, // protocolId
			sn, // userReadableName
			sn + "Req", // payloadType
			sn + "Res" // responseType
		));

		// namesToExport.add(sn + "Req");
		// namesToExport.add(sn + "Res");
	}

	private void genKlDef(Output out, String prefix, Streamable s) throws Exception {

		for (Field f : s.getClass().getFields()) {
			f.setAccessible(true);
			out.print(prefix);
			final Object val = f.get(s);
			final int preParseSize = out.parseCmds.size();
			final int preEncodeSize = out.encodeCmds.size();
			if (val instanceof Optional<?>) {
				genTypescriptRef(out, prefix, ((Optional<?>) val).get());
				out.print("?");
				final int posParse = out.parseCmds.size();
				if (preParseSize != posParse) {
					final String cmd = out.parseCmds.remove(preParseSize);
					if (cmd.endsWith("?: fail")) {
						out.parseCmds.add(cmd.substring(0, cmd.length() - "?: fail".length()));
					} else {
						out.parseCmds.add(cmd);
					}
				}
				final int posEncode = out.encodeCmds.size();
				if (preEncodeSize != posEncode) {
					final String cmd = out.encodeCmds.remove(preEncodeSize);
					if (cmd.contains(" VAL")) {
						out.encodeCmds.add("if ($v = " + f.getName() +"): " + cmd.replace(" VAL", "v"));
					} else {
						out.encodeCmds.add(cmd);
					}
				}
			} else {
				genTypescriptRef(out, prefix, val);
			}
			out.rewriteLastParseCmd(preParseSize, preEncodeSize, f.getName());
			out.println(" " + f.getName() +",");
		}
	}

	private void genTypescriptRef(Output out, String prefix, Object rawRef) throws Exception {
		if (rawRef instanceof Class<?>) {
			Class<?> clazz = (Class<?>) rawRef;
			if (clazz == String.class) {
				out.print("String");
				out.parseCmds.add("getStr() ?: fail");
				out.encodeCmds.add("dst.set(.str: VAL);");
			} else if (clazz == Integer.class) {
				out.print("int");
				out.parseCmds.add("getInt() ?: fail");
				out.encodeCmds.add("dst.set(.num: float( VAL));");
			} else if (clazz == Long.class) {
				out.print("long");
				out.parseCmds.add("getLong() ?: fail");
				out.encodeCmds.add("dst.set(.num: float( VAL));");
			} else if (clazz == Boolean.class) {
				out.print("bool");
				out.parseCmds.add("getBool() ?: fail");
				out.encodeCmds.add("dst.set(.boolean: VAL);");
			} else if (clazz.isEnum()) {
				if (!target.containsKey(clazz.getSimpleName())) {
					final Output subOut = addOutputEntry(clazz.getSimpleName());
					subOut.print("enum " + clazz.getSimpleName() +": ");
					for (Object ev : clazz.getEnumConstants()) {
						subOut.print("\"" + ev.toString() + "\", ");
					}
					subOut.println("{");
					subOut.println("  JsonValue getJson(): .str: \"$this\";");;
					subOut.println("}");
					parseSrcContents.append("  " + clazz.getSimpleName() +"? namedGet" + clazz.getSimpleName() + "(String name): .fromString(src.obj?.getStr(name) ?: fail);\n");
					parseSrcContents.append("  " + clazz.getSimpleName() +"? get" + clazz.getSimpleName() + "(): .fromString(src.str ?: fail);\n");
				}
				out.print(clazz.getSimpleName());
				out.parseCmds.add("get" + clazz.getSimpleName() + "() ?: fail");
				out.encodeCmds.add("dst.set( VAL.json);");
			} else if (clazz == Object.class) {
				out.print("any");
			} else if (clazz == JSONObject.class) {
				out.print("JsonObject");
				out.parseCmds.add("getObj() ?: fail");
				out.encodeCmds.add("dst.set(.obj: VAL);");
			} else if (clazz == Void.class) {
				out.print("null");
			} else {
				if (StreamableUnion.class.isAssignableFrom(clazz)) {
					final StreamableUnion su = (StreamableUnion) clazz.newInstance();
					requestedTypes.add(clazz);
					final String sn = su.getClass().getSimpleName();
					out.print(sn);
					out.parseCmds.add("get" + sn + "() ?: fail");
					out.encodeCmds.add("dst.set( VAL.json);");
				} else if (Streamable.class.isAssignableFrom(clazz)) {
					requestedTypes.add(clazz);
					final String sn = clazz.getSimpleName();
					out.print(sn);
					out.parseCmds.add("get" + sn + "() ?: fail");
					out.encodeCmds.add("dst.set( VAL.json);");
				} else {
					throw new Exception("Invalid class " + clazz);
				}
			}
		} else if (rawRef instanceof String) {
			out.print("// \"" + rawRef + "\"");
		} else if (rawRef instanceof StreamableUnion) {
			requestedTypes.add(rawRef);
			out.print(((StreamableUnion) rawRef).getClass().getSimpleName());
		} else if (rawRef instanceof Streamable) {
			final Output subOut = out.allocAnonymousChild();
			subOut.println("struct " + subOut.typeName + ":");
			genKlDef(subOut, prefix + "  ", (Streamable) rawRef);
			subOut.println("{");

			subOut.println("  static " + subOut.typeName +"? parse(ParseSrc src): new(");
			for (String cmd : subOut.parseCmds) {
				subOut.println("    src." + cmd +",");
			}
			subOut.println("  );");
			subOut.println("  JsonValue getJson(): .obj: .o(dst -> {");
			for (String cmd : subOut.encodeCmds) {
				subOut.println("    " + cmd);
			}
			subOut.println("    return dst;");
			subOut.println("  });");
			subOut.println("}");
			out.parseCmds.add("get" + subOut.typeName +"() ?: fail");
			out.encodeCmds.add("dst.set( VAL.json);");
			parseSrcContents.append("  " + subOut.typeName +"? namedGet" + subOut.typeName + "(String name): .parse(new(src.obj?.get(name) ?: fail));\n");
			parseSrcContents.append("  " + subOut.typeName +"? get" + subOut.typeName + "(): .parse(this);\n");

			out.print(subOut.typeName);
		} else if (rawRef instanceof Optional<?>) {
			System.err.println("Unexpected optional");
			System.exit(1);
			// genTypescriptRef(out, prefix, ((Optional<?>) rawRef).get());
			// out.print("?");
		} else if (rawRef instanceof Object[]) {
			final Object[] opt = (Object[]) rawRef;
			if (opt.length <= 1) {
				throw new Error("Optionals must have at least two entries, got " + opt.length);
			}
			final boolean isEnumLike = opt[0] instanceof String;
			if (isEnumLike) {
				final Output subOut = out.allocAnonymousChild();
				subOut.print("enum " + subOut.typeName + ": ");
				for (int i = 0; i < opt.length; i++) {
					subOut.print("\"" + opt[i] + "\"");
					// genTypescriptRef(subOut, prefix, opt[i]);
					subOut.print(", ");
				}
				subOut.println("{");
				subOut.println("  JsonValue getJson(): .str: \"$this\";");;
				subOut.println("}");

				out.parseCmds.add("get" + subOut.typeName +"() ?: fail");
				parseSrcContents.append("  " + subOut.typeName +"? namedGet" + subOut.typeName + "(String name): .fromString(src.obj?.getStr(name) ?: fail);\n");
				parseSrcContents.append("  " + subOut.typeName +"? get" + subOut.typeName + "(): .fromString(src.str ?: fail);\n");

				out.print(subOut.typeName);
				return;
			}
			throw new Exception("Unknown object array type: " + Arrays.toString(opt));

		} else if (rawRef instanceof List<?>) {
			List<?> l = (List<?>) rawRef;
			if (l.size() != 1) {
				throw new Error("Lists must have a single entry, got " + l.size());
			}
			final int prePSize = out.parseCmds.size();
			final int preESize = out.encodeCmds.size();
			out.print("List<");
			final String preRes = out.sb.toString();
			genTypescriptRef(out, prefix, l.get(0));
			final String posRes = out.sb.toString();
			out.print(">");

			if (out.parseCmds.size() != prePSize) {
				// Added a parseable field, rewrite it to use a list variant
				final String typeRef = posRes.substring(preRes.length());
				final String cmd = out.parseCmds.remove(prePSize);
				out.parseCmds.add("getList<" + typeRef + ">(:." + cmd + ") ?: fail");
			}
			if (out.encodeCmds.size() != preESize) {
				// Added a encodable field, rewrite it to use a list variant
				final String cmd = out.encodeCmds.remove(preESize);
				out.encodeCmds.add(cmd
					.replace(" VAL", " inner")
					.replace(".set(", ".set(.arr: VAL.map<JsonValue>(inner: ")
					.replace(");", "));")
				);
			}

		} else {
			throw new Error("TODO encode " + rawRef);
		}
	}
}
