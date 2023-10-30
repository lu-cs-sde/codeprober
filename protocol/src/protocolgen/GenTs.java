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
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.json.JSONObject;

import protocolgen.spec.Rpc;
import protocolgen.spec.Streamable;
import protocolgen.spec.StreamableUnion;

public class GenTs {

	public static void gen(List<Class<? extends Rpc>> rpcs, List<Class<? extends Streamable>> serverToClient)
			throws Exception {
		System.out.println("== GEN TS");
		final GenTs gt = new GenTs();
		for (Class<? extends Rpc> rpc : rpcs) {
			gt.genTypescriptDef(rpc.newInstance());
		}
		for (Class<? extends Streamable> cls : serverToClient) {
			gt.requestedTypes.add(cls);
		}
		gt.drainRequests();

		StringBuilder fullFile = new StringBuilder();
		final ArrayList<Entry<String, StringBuilder>> entries = new ArrayList<>(gt.target.entrySet());
		entries.sort((a, b) -> a.getKey().compareTo(b.getKey()));
		for (Entry<String, StringBuilder> ent : entries) {
			fullFile.append(ent.getValue().toString());
		}
		fullFile.append(String.format("\n\nexport {\n   %s\n}\n", gt.namesToExport.stream().collect(Collectors.joining("\n , "))));

		Files.write(gt.getDstFile().toPath(), fullFile.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
		System.out.println("Done");
	}

	private final Map<String, StringBuilder> target = new HashMap<>();
	private final Set<String> namesToExport = new TreeSet<>();

	private File getDstFile() throws Exception {
		final String prop = System.getProperty("TS_DST_FILE");
		if (prop == null) {
			throw new Exception("Missing value for system property TS_DST_FILE");
		}
		final File dstFile = new File(prop);
		if (!dstFile.getParentFile().exists()) {
			throw new Exception("Bad value for system property TS_DST_FILE");
		}
		return dstFile;
	}

//	private void println(String line) {
//		print(line + "\n");
//	}
//
//	private void print(String msg) {
//		target.append(msg);
//	}

	private Output addOutputEntry(String name) {
		final StringBuilder sb = new StringBuilder();
		target.put(name, sb);
		return new Output(sb);
	}

	private class Output {
		private StringBuilder sb;

		public Output(StringBuilder sb) {
			this.sb = sb;
		}

		public void print(String msg) {
			this.sb.append(msg);
		}

		public void println(String line) {
			print(line + "\n");
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
						out.println("interface " + clazz.getSimpleName() + " {");
						namesToExport.add(clazz.getSimpleName());
						genTypescriptDef(out, "  ", (Streamable) clazz.newInstance());
						out.println("}");
						continue;
					}
				}

				if (req instanceof StreamableUnion) {
					StreamableUnion su = (StreamableUnion) req;
					final Output out = addOutputEntry(su.getClass().getSimpleName());
					out.print(String.format("type %s = (\n", su.getClass().getSimpleName()));
					namesToExport.add(su.getClass().getSimpleName());
					boolean first = true;
					for (Field f : su.getClass().getFields()) {
						f.setAccessible(true);
						final String fname = f.getName();
						if (first) {
							out.print("    ");
							first = false;
						} else {
							out.print("  | ");
						}
						out.print("{ type: '" + fname + "'; value: ");
						genTypescriptRef(out, "", f.get(su));
						out.println("; }");
					}
					out.println(");");
				} else {
					throw new Error("Bad request '" + req + "'");
				}
			}
		}
	}

	private Set<Object> requestedTypes = new LinkedHashSet<>();

	private <Arg extends Streamable, Res extends Streamable> void genTypescriptDef(Rpc rpc) throws Exception {
		final String sn = rpc.getClass().getSimpleName();
		final Output out = addOutputEntry(sn);
		out.println("interface " + sn + "Req {");
		genTypescriptDef(out, "  ", rpc.getRequestType());
		out.println("}");
		out.println("interface " + sn + "Res {");
		genTypescriptDef(out, "  ", rpc.getResponseType());
		out.println("}");

		namesToExport.add(sn + "Req");
		namesToExport.add(sn + "Res");
	}

	private void genTypescriptDef(Output out, String prefix, Streamable s) throws Exception {

		for (Field f : s.getClass().getFields()) {
			f.setAccessible(true);
			out.print(prefix + f.getName());
			final Object val = f.get(s);
			if (val instanceof Optional<?>) {
				out.print("?: ");
				genTypescriptRef(out, prefix, ((Optional<?>) val).get());
			} else {
				out.print(": ");
				genTypescriptRef(out, prefix, val);
			}
			out.println(";");
		}
	}

	private void genTypescriptRef(Output out, String prefix, Object rawRef) throws Exception {
		if (rawRef instanceof Class<?>) {
			Class<?> clazz = (Class<?>) rawRef;
			if (clazz == String.class) {
				out.print("string");
			} else if (clazz == Integer.class) {
				out.print("number");
			} else if (clazz == Long.class) {
				out.print("number");
			} else if (clazz == Boolean.class) {
				out.print("boolean");
			} else if (clazz.isEnum()) {
				boolean first = true;
				out.print("(");
				for (Object ev : clazz.getEnumConstants()) {
					if (first) {
						first = false;
					} else {
						out.print("| ");
					}
					out.print("'" + ev.toString() + "'");
				}
				out.print(")");
			} else if (clazz == Object.class) {
				out.print("any");
			} else if (clazz == JSONObject.class) {
				out.print("{ [key: string]: any }");
			} else if (clazz == Void.class) {
				out.print("null");
			} else {
				if (StreamableUnion.class.isAssignableFrom(clazz)) {
					final StreamableUnion su = (StreamableUnion) clazz.newInstance();
					requestedTypes.add(clazz);
					out.print(su.getClass().getSimpleName());
				} else if (Streamable.class.isAssignableFrom(clazz)) {
					requestedTypes.add(clazz);
					out.print(clazz.getSimpleName());
				} else {
					throw new Exception("Invalid class " + clazz);
				}
			}
		} else if (rawRef instanceof String) {
			out.print("\"" + rawRef + "\"");
		} else if (rawRef instanceof StreamableUnion) {
			requestedTypes.add(rawRef);
			out.print(((StreamableUnion) rawRef).getClass().getSimpleName());
		} else if (rawRef instanceof Streamable) {
			out.println("{");
			genTypescriptDef(out, prefix + "  ", (Streamable) rawRef);
			out.print(prefix + "}");
		} else if (rawRef instanceof Optional<?>) {
			out.print("undefined | ");
			genTypescriptRef(out, prefix, ((Optional<?>) rawRef).get());
		} else if (rawRef instanceof Object[]) {
			final Object[] opt = (Object[]) rawRef;
			if (opt.length <= 1) {
				throw new Error("Optionals must have at least two entries, got " + opt.length);
			}
			final boolean isEnumLike = opt[0] instanceof String;
			if (isEnumLike) {
				out.print("(");
				for (int i = 0; i < opt.length; i++) {
					if (i > 0) {
						out.print(" | ");
					}
					genTypescriptRef(out, prefix, opt[i]);
				}
				out.print(")");
				return;
			}
			throw new Exception("Unknown object array type: " + Arrays.toString(opt));

		} else if (rawRef instanceof List<?>) {
			List<?> l = (List<?>) rawRef;
			if (l.size() != 1) {
				throw new Error("Lists must have a single entry, got " + l.size());
			}
			genTypescriptRef(out, prefix, l.get(0));
			out.print("[]");
		} else {
			throw new Error("TODO encode " + rawRef);
		}

	}
}
