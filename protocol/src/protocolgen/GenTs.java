package protocolgen;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

		String fullFile = gt.target
				+ String.format("\n\nexport { %s }\n", gt.namesToExport.stream().collect(Collectors.joining(", ")));

		Files.write(gt.getDstFile().toPath(), fullFile.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
		System.out.println("Done");
	}

	private final StringBuilder target = new StringBuilder();
	private final Set<String> namesToExport = new HashSet<>();

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

	private void println(String line) {
		print(line + "\n");
	}

	private void print(String msg) {
		target.append(msg);
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
						println("interface " + clazz.getSimpleName() + " {");
						namesToExport.add(clazz.getSimpleName());
						genTypescriptDef("  ", (Streamable) clazz.newInstance());
						println("}");
						continue;
					}
				}

				if (req instanceof StreamableUnion) {
					StreamableUnion su = (StreamableUnion) req;
					print(String.format("type %s = (\n", su.getClass().getSimpleName()));
					namesToExport.add(su.getClass().getSimpleName());
					boolean first = true;
					for (Field f : su.getClass().getFields()) {
						f.setAccessible(true);
						final String fname = f.getName();
						if (first) {
							print("    ");
							first = false;
						} else {
							print("  | ");
						}
						print("{ type: '" + fname + "'; value: ");
						genTypescriptRef("", f.get(su));
						println("; }");
					}
					println(");");
				} else {
					throw new Error("Bad request '" + req + "'");
				}
			}
		}
	}

	private Set<Object> requestedTypes = new HashSet<>();

	private <Arg extends Streamable, Res extends Streamable> void genTypescriptDef(Rpc rpc) throws Exception {
		final String sn = rpc.getClass().getSimpleName();
		println("interface " + sn + "Req {");
		genTypescriptDef("  ", rpc.getRequestType());
		println("}");
		println("interface " + sn + "Res {");
		genTypescriptDef("  ", rpc.getResponseType());
		println("}");

		namesToExport.add(sn + "Req");
		namesToExport.add(sn + "Res");
	}

	private void genTypescriptDef(String prefix, Streamable s) throws Exception {

		for (Field f : s.getClass().getFields()) {
			f.setAccessible(true);
			print(prefix + f.getName());
			final Object val = f.get(s);
			if (val instanceof Optional<?>) {
				print("?: ");
				genTypescriptRef(prefix, ((Optional<?>) val).get());
			} else {
				print(": ");
				genTypescriptRef(prefix, val);
			}
			println(";");
		}
	}

	private void genTypescriptRef(String prefix, Object rawRef) throws Exception {
		if (rawRef instanceof Class<?>) {
			Class<?> clazz = (Class<?>) rawRef;
			if (clazz == String.class) {
				print("string");
			} else if (clazz == Integer.class) {
				print("number");
			} else if (clazz == Long.class) {
				print("number");
			} else if (clazz == Boolean.class) {
				print("boolean");
			} else if (clazz.isEnum()) {
				boolean first = true;
				print("(");
				for (Object ev : clazz.getEnumConstants()) {
					if (first) {
						first = false;
					} else {
						print("| ");
					}
					print("'" + ev.toString() + "'");
				}
				print(")");
			} else if (clazz == Object.class) {
				print("any");
			} else if (clazz == JSONObject.class) {
				print("{ [key: string]: any }");
			} else if (clazz == Void.class) {
				print("null");
			} else {
				if (StreamableUnion.class.isAssignableFrom(clazz)) {
					final StreamableUnion su = (StreamableUnion) clazz.newInstance();
					requestedTypes.add(clazz);
					print(su.getClass().getSimpleName());
				} else if (Streamable.class.isAssignableFrom(clazz)) {
					requestedTypes.add(clazz);
					print(clazz.getSimpleName());
				} else {
					throw new Exception("Invalid class " + clazz);
				}
			}
		} else if (rawRef instanceof String) {
			print("\"" + rawRef + "\"");
		} else if (rawRef instanceof StreamableUnion) {
			requestedTypes.add(rawRef);
			print(((StreamableUnion) rawRef).getClass().getSimpleName());
		} else if (rawRef instanceof Streamable) {
			println("{");
			genTypescriptDef(prefix + "  ", (Streamable) rawRef);
			print(prefix + "}");
		} else if (rawRef instanceof Optional<?>) {
			print("undefined | ");
			genTypescriptRef(prefix, ((Optional<?>) rawRef).get());
		} else if (rawRef instanceof Object[]) {
			final Object[] opt = (Object[]) rawRef;
			if (opt.length <= 1) {
				throw new Error("Optionals must have at least two entries, got " + opt.length);
			}
			final boolean isEnumLike = opt[0] instanceof String;
			if (isEnumLike) {
				print("(");
				for (int i = 0; i < opt.length; i++) {
					if (i > 0) {
						print(" | ");
					}
					genTypescriptRef(prefix, opt[i]);
				}
				print(")");
				return;
			}
			throw new Exception("Unknown object array type: " + Arrays.toString(opt));

		} else if (rawRef instanceof List<?>) {
			List<?> l = (List<?>) rawRef;
			if (l.size() != 1) {
				throw new Error("Lists must have a single entry, got " + l.size());
			}
			genTypescriptRef(prefix, l.get(0));
			print("[]");
		} else {
			throw new Error("TODO encode " + rawRef);
		}

	}
}
