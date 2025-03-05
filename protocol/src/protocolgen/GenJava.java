package protocolgen;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.json.JSONObject;

import protocolgen.spec.Rpc;
import protocolgen.spec.Streamable;
import protocolgen.spec.StreamableUnion;

public class GenJava {

	private static enum FieldKind {
		PRIMITIVE, STREAMABLE, STRING_CONSTANT, NULLPOINTER, ENUM, OPTIONAL_PRIMITIVE, OPTIONAL_STREAMABLE,
		OPTIONAL_LIST_OF_STREAMABLES, OPTIONAL_LIST_OF_PRIMITIVES, OPTIONAL_ENUM, LIST_OF_PRIMTIIVES,
		LIST_OF_STREAMABLES;

		boolean isConstant() {
			switch (this) {
			case STRING_CONSTANT:
			case NULLPOINTER:
				return true;
			default:
				return false;
			}
		}

		boolean isOptional() {
			switch (this) {
			case OPTIONAL_ENUM:
			case OPTIONAL_LIST_OF_PRIMITIVES:
			case OPTIONAL_LIST_OF_STREAMABLES:
			case OPTIONAL_PRIMITIVE:
			case OPTIONAL_STREAMABLE:
				return true;
			default:
				return false;
			}
		}
	}

	@FunctionalInterface
	interface WriteGenerator {
		String genWrite(String jsonObj, String field, String valPtr);
	}

	private static class GeneratedType {
		public final String name;
		public final FieldKind kind;
		public final BiFunction<String, String, String> genReadFromJsonObj;
		public final WriteGenerator genWriteToJson;
		public final BiFunction<String, String, String> genReadFromDataStream;
		public final WriteGenerator genWriteToDataStream;
		public final BiConsumer<String, String> genCompareTo;

		public GeneratedType(String name, FieldKind kind, BiFunction<String, String, String> genReadFromJsonObj,
				WriteGenerator genWriteToJson, BiFunction<String, String, String> genReadFromDataStream,
				WriteGenerator genWriteToDataStream, BiConsumer<String, String> genCompareTo) {
			this.name = name;
			this.kind = kind;
			this.genReadFromJsonObj = genReadFromJsonObj;
			this.genWriteToJson = genWriteToJson;
			this.genReadFromDataStream = genReadFromDataStream;
			this.genWriteToDataStream = genWriteToDataStream;
			this.genCompareTo = genCompareTo;
		}

		public String getBoxedName() {
			switch (name) {
			case "int":
				return "Integer";
			case "long":
				return "Long";
			case "boolean":
				return "Boolean";
			default:
				return name;
			}
		}

	}

	private abstract class RequestedType {

		public abstract String getTypeName();

		protected abstract Object[] getComparisonStuff();

		@Override
		public int hashCode() {
			return Objects.hash(getComparisonStuff());
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof RequestedType && obj.getClass() == this.getClass()
					&& Arrays.equals(getComparisonStuff(), ((RequestedType) obj).getComparisonStuff());
		}

		protected abstract String generate() throws Exception;
	}

	private class NormalRequestedType extends RequestedType {

		private final Streamable instance;
		private final String typeName;

		public NormalRequestedType(Streamable instance) {
			this(instance, instance.getClass().getSimpleName());
		}

		public NormalRequestedType(Streamable instance, String typeName) {
			this.instance = instance;
			this.typeName = typeName;
		}

		@Override
		public String getTypeName() {
			return typeName;
		}

		@Override
		protected Object[] getComparisonStuff() {
			return new Object[] { instance.getClass() };
		}

		@Override
		protected String generate() throws Exception {
			return genTypescriptDef(getTypeName(), instance);
		}
	}

	private class AnonRequestedType extends RequestedType {

		private final Streamable instance;

		public AnonRequestedType(Streamable instance) {
			this.instance = instance;
		}

		@Override
		public String getTypeName() {
			return instance.getClass().getName().replace('$', '_').replace('.', '_');
		}

		@Override
		protected Object[] getComparisonStuff() {
			return new Object[] { instance.getClass() };
		}

		@Override
		protected String generate() throws Exception {
			return genTypescriptDef(getTypeName(), instance);
		}
	}

	private static class UnionMember {
		public final String name;
		public final Object value;
//		private final BiFunction<String, String, String> genCompareTo;

		public UnionMember(String name, Object value) {
			this.name = name;
			this.value = value;
//			this.genCompareTo = genCompareTo;
		}
	}

	private class UnionRequestedType extends RequestedType {

//		private final StreamableUnion union;
		private final String typeName;
		private final List<UnionMember> members;

		public UnionRequestedType(StreamableUnion union) throws Exception {
			this.typeName = union.getClass().getSimpleName();
			this.members = new ArrayList<>();
			for (Field f : listFieldsInProperOrder(union.getClass())) {
				f.setAccessible(true);
				members.add(new UnionMember(f.getName(), f.get(union)));
			}
		}

		@Override
		public String getTypeName() {
			return typeName;
		}

		@Override
		protected Object[] getComparisonStuff() {
			return new Object[] { typeName };
		}

		@Override
		protected String generate() throws Exception {
//			final List<Field> fields = listFieldsInProperOrder(union.getClass());

			final List<GeneratedType> types = new ArrayList<>();
			boolean anyGenericField = false;
			for (UnionMember f : members) {
				final GeneratedType gen = genRef(f.value);
				types.add(gen);
				anyGenericField |= gen.name.contains("<");
			}

			final StringBuilder out = new StringBuilder();
			final Consumer<String> println = line -> out.append(line + "\n");
//			final Consumer<String> print = line -> out.append(line);
			println.accept(getAutoGenDisclaimerLine());
			println.accept("package " + getDstPkg() + ";");
			println.accept("");
			println.accept("import org.json.JSONObject;");
			println.accept("");

			if (anyGenericField)
				println.accept("@SuppressWarnings(\"unchecked\")");
			println.accept("public class " + getTypeName() + " implements codeprober.util.JsonUtil.ToJsonable {");

			println.accept("  public static enum Type {");
			for (UnionMember f : members) {
				println.accept("    " + f.name + ",");
			}
			println.accept("  }");
			println.accept("  private static final Type[] typeValues = Type.values();");
			println.accept("");
			println.accept("  public final Type type;");
			println.accept("  public final Object value;");
			println.accept("  private " + getTypeName() + "(Type type, Object value) {");
			println.accept("    this.type = type;");
			println.accept("    this.value = value;");
			println.accept("  }");
			println.accept("  public " + getTypeName() + "(java.io.DataInputStream src) throws java.io.IOException {");
			println.accept("    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));");
			println.accept("  }");
			println.accept("  public " + getTypeName() + "(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {");
			println.accept("    this.type = typeValues[src.readInt()];");
			println.accept("    switch (this.type) {");
			for (int i = 0; i < members.size(); i++) {
				final String fn = members.get(i).name;
				println.accept("    case " + fn + ":");
				if (i == members.size() - 1) {
					println.accept("    default:");
				}
//				final String capped = fn.substring(0, 1).toUpperCase(Locale.ENGLISH) + fn.substring(1);
				println.accept("        this.value = " + types.get(i).genReadFromDataStream.apply("src", "UNUSED") + ";");
				println.accept("        break;");
			}
			println.accept("    }");
			println.accept("  }");

			for (int i = 0; i < members.size(); ++i) {
				final String fn = members.get(i).name;
				final String capped = fn.substring(0, 1).toUpperCase(Locale.ENGLISH) + fn.substring(1);
				println.accept("  public static " + getTypeName() + " from" + capped + "(" + types.get(i).name
						+ " val) { return new " + getTypeName() + "(Type." + members.get(i).name + ", val); }");
			}
			println.accept("");
			for (int i = 0; i < members.size(); ++i) {
				final String sn = types.get(i).name;
				final String fn = members.get(i).name;
				final String capped = fn.substring(0, 1).toUpperCase(Locale.ENGLISH) + fn.substring(1);
				println.accept("  public boolean is" + capped + "() { return type == Type." + fn + "; }");
				println.accept("  public " + sn + " as" + capped + "() { if (type != Type." + fn
						+ ") { throw new IllegalStateException(\"This " + getTypeName() + " is not of type " + fn
						+ ", it is '\" + type + \"'\"); } return (" + sn + ")value; }");
			}
			println.accept("");
			println.accept("  public static " + getTypeName() + " fromJSON(JSONObject obj) {");
			println.accept("    final Type type;");
			println.accept("    try { type = Type.valueOf(obj.getString(\"type\")); }");
			println.accept("    catch (IllegalArgumentException e) { throw new org.json.JSONException(e); }");
			println.accept("    switch (type) {");

			for (int i = 0; i < members.size(); i++) {
				final String fn = members.get(i).name;
				println.accept("    case " + fn + ":");
				if (i == members.size() - 1) {
					println.accept("    default:");
				}
				final String capped = fn.substring(0, 1).toUpperCase(Locale.ENGLISH) + fn.substring(1);
				println.accept("      try {");
				println.accept("        final " + types.get(i).name + " val = "
						+ types.get(i).genReadFromJsonObj.apply("obj", "\"value\"") + ";");
				println.accept("        return from" + capped + "(val);");
				println.accept("      } catch (org.json.JSONException e) {");
				println.accept("        throw new org.json.JSONException(\"Not a valid " + getTypeName() + "\", e);");
				println.accept("      }");
			}
			println.accept("    }");
			println.accept("  }");
			println.accept("");
			println.accept("  public JSONObject toJSON() {");
			println.accept("    final JSONObject ret = new JSONObject().put(\"type\", type.name());");
			println.accept("    switch (type) {");
			for (int i = 0; i < members.size(); i++) {
				println.accept("    case " + members.get(i).name + ":");
				if (i == members.size() - 1) {
					println.accept("    default:");
				}
//				switch (types.get(i).kind) {
//				case OPTIONAL_ENUM:
				println.accept("      " + types.get(i).genWriteToJson.genWrite("ret", "\"value\"",
						"((" + types.get(i).name + ")value)"));
//				}
				println.accept("      break;");
//				println.accept("        return as" + sn + "().toJSON();");
			}

			println.accept("    }");
			println.accept("    return ret;");
			println.accept("  }");
			println.accept("  public int compareTo(" + getTypeName() + " other) {");
			println.accept("    if (type != other.type) { return Integer.compare(type.ordinal, other.type.ordinal); }");
			println.accept("  }");
			println.accept("  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {");
			println.accept("    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));");
			println.accept("  }");
			println.accept("  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {");
			println.accept("    dst.writeInt(type.ordinal());");
			println.accept("    switch (type) {");
			for (int i = 0; i < members.size(); i++) {
				println.accept("    case " + members.get(i).name + ":");
				if (i == members.size() - 1) {
					println.accept("    default:");
				}
				println.accept("      " + types.get(i).genWriteToDataStream.genWrite("dst", "UNUSED",
						"((" + types.get(i).name + ")value)"));
				println.accept("      break;");
			}

			println.accept("    }");
			println.accept("  }");
			println.accept("}");
			return out.toString();
		}

	}

	public static void gen(final List<Class<? extends Rpc>> rpcs, List<Class<? extends Streamable>> serverToClient)
			throws Exception {
		System.out.println("== GEN JAVA");

//		final Rpc<?, ?> ln = new ListNodes();
		final GenJava gt = new GenJava();
		for (Class<? extends Streamable> c : serverToClient) {
			gt.requestedTypes.add(gt.new NormalRequestedType(c.newInstance()));
		}
		final File[] existingListing = gt.getDstDir().listFiles();
		if (existingListing != null) {
			for (File existing : existingListing) {
				existing.delete();
			}
		}

		for (Class<? extends Rpc> rpc : rpcs) {
			gt.genTypescriptDef(rpc.newInstance());
		}
		gt.drainRequests();

		final String requestAdapter = new Object() {
			final StringBuilder builder = new StringBuilder();

			void println(String line) {
				builder.append(line + "\n");
			}

			String gen() throws Exception {
				println(getAutoGenDisclaimerLine());
				println("package " + gt.getDstPkg() + ";");
				println("");
				println("import org.json.JSONException;");
				println("import org.json.JSONObject;");
				println("");
				println("public abstract class RequestAdapter {");
				println("");
				println("  public JSONObject handle(JSONObject request) {");
				println("    switch (request.getString(\"type\")) {");
				final Set<String> encounteredTypes = new HashSet<>();
				for (Class<? extends Rpc> rpcClass : rpcs) {
					final Rpc rpc = rpcClass.newInstance();
					final String sn = rpc.getClass().getSimpleName();
					final Consumer<String> handleStringCase = caseVal -> {
						if (encounteredTypes.contains(caseVal)) {
							throw new RuntimeException("Overlapping 'type' val " + caseVal);
						}
						encounteredTypes.add(caseVal);
						println("      case \"" + caseVal + "\": {");
						println("        return handle" + sn + "(" + sn + "Req.fromJSON(request)).toJSON();");
						println("      }");
					};
					final Streamable req = rpc.getRequestType();
					final Field typeField = req.getClass().getField("type");
					typeField.setAccessible(true);
					final Object typeVal = typeField.get(req);
					if (typeVal instanceof String) {
						handleStringCase.accept((String) typeVal);
					} else if (typeVal instanceof Object[]) {
						final Object[] vals = (Object[]) typeVal;
						for (Object val : vals) {
							if (val instanceof String) {
								handleStringCase.accept((String) val);
							} else {
								throw new Error("Invalid 'type' value on rpc type " + sn);
							}
						}
					} else {
						throw new Error("Invalid 'type' value on rpc type " + sn);
					}
				}
				println("      default: return null;");
				println("    }");
				println("  }");
				for (Class<? extends Rpc> rpcClass : rpcs) {
					final String sn = rpcClass.getSimpleName();

					println("");
					println("  protected " + sn + "Res handle" + sn + "(" + sn + "Req req" + ") {");
					println("    throw new JSONException(\"Request " + sn + " is not implemented\");");
					println("  }");
				}
				println("}");

				return builder.toString();
			}
		}.gen();

		Files.write(new File(gt.getDstDir(), "RequestAdapter.java").toPath(),
				requestAdapter.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);

//		println.accept(null);

		System.out.println("Done");
	}

	private void drainRequests() throws Exception {
		Set<RequestedType> generated = new HashSet<>();

		while (generated.size() != requestedTypes.size()) {
			for (RequestedType req : new ArrayList<>(requestedTypes)) {
				if (generated.contains(req)) {
					continue;
				}
//				System.out.println("public class " + clazz.getSimpleName() + " {");
//				final Streamable inst = (Streamable) req.clazz.newInstance();
//				req.generate();

				final String resName = req.getTypeName();
				final String contents = req.generate();
				Files.write(new File(getDstDir(), resName + ".java").toPath(),
						contents.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING);

//				System.out.println("}");
				generated.add(req);
			}
		}
	}

	private Set<RequestedType> requestedTypes = new HashSet<>();

	private File getDstDir() throws Exception {
		final String dstDirStr = System.getProperty("JAVA_DST_DIR");
		if (dstDirStr == null) {
			throw new Exception("Missing value for system property JAVA_DST_DIR");
		}
		final File dstDir = new File(dstDirStr);
		if (!dstDir.exists()) {
			throw new Exception("Bad value for system property JAVA_DST_DIR");
		}
		return dstDir;
	}

	private <Arg extends Streamable, Res extends Streamable> void genTypescriptDef(Rpc rpc) throws Exception {

		final String rpcName = rpc.getClass().getSimpleName();

		final String argName = rpcName + "Req";
		final String argContents = genTypescriptDef(rpcName + "Req", rpc.getRequestType());
		Files.write(new File(getDstDir(), argName + ".java").toPath(), argContents.getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		final String resName = rpcName + "Res";
		final String resContents = genTypescriptDef(rpcName + "Res", rpc.getResponseType());
		Files.write(new File(getDstDir(), resName + ".java").toPath(), resContents.getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private String getDstPkg() throws Exception {
		final String pkg = System.getProperty("JAVA_DST_PKG");
		if (pkg == null) {
			throw new Exception("Missing/bad value for JAVA_DST_PKG");
		}
		return pkg;
	}

	private List<Field> listFieldsInProperOrder(Class<?> clazz) {
		final List<Field> local = Arrays.asList(clazz.getDeclaredFields()).stream()
				.filter(f -> Modifier.isPublic(f.getModifiers())).collect(Collectors.toList());
		for (Field f : local) {
			f.setAccessible(true);
		}
		final Class<?> supe = clazz.getSuperclass();
		if (supe == null) {
			return local;
		}
		final List<Field> supeList = listFieldsInProperOrder(supe);
		supeList.addAll(local);
		return supeList;
	}

	private String genTypescriptDef(String tname, Streamable s) throws Exception {
		final StringBuilder out = new StringBuilder();
		final Consumer<String> println = line -> out.append(line + "\n");
		final Consumer<String> print = line -> out.append(line);

		println.accept("package " + getDstPkg() + ";");

		println.accept("");
		println.accept("import org.json.JSONObject;");
		println.accept("");
		println.accept("public class " + tname + " implements codeprober.util.JsonUtil.ToJsonable {");
		final Class<?> clazz = s.getClass();
		final List<GeneratedType> refs = new ArrayList<>();
		final List<Field> fields = listFieldsInProperOrder(clazz);
		for (Field f : fields) {
			try {
				refs.add(genRef(f.get(s)));
			} catch (Exception e) {
				System.err.println("Error when generataing field " + tname + "." + f.getName());
				throw e;
			}
		}

		for (int i = 0; i < fields.size(); i++) {
			println.accept("  public final " + refs.get(i).name + " " + fields.get(i).getName() + ";");
		}

		for (int optionalBackoff = fields.size() - 1; optionalBackoff >= 0; optionalBackoff--) {
			if (!refs.get(optionalBackoff).kind.isOptional()) {
				break;
			}
			// Generate special constructor that forwards to the real one
			print.accept("  public " + tname + "(");
			int numAddedArgs = 0;
			for (int i = 0; i < optionalBackoff; i++) {
				if (refs.get(i).kind.isConstant()) {
					continue;
				}
				if (numAddedArgs > 0) {
					print.accept(", ");
				}
				print.accept(refs.get(i).name + " " + fields.get(i).getName());
				++numAddedArgs;
			}
			println.accept(") {");
			int numForwardedArgs = 0;
			print.accept("    this(");
			for (int i = 0; i < optionalBackoff; ++i) {
				if (refs.get(i).kind.isConstant()) {
					continue;
				}
				if (numForwardedArgs > 0) {
					print.accept(", ");
				}
				print.accept(fields.get(i).getName());
				++numForwardedArgs;
			}
			for (int i = optionalBackoff; i < fields.size(); ++i) {
				if (refs.get(i).kind.isConstant()) {
					continue;
				}
				if (numForwardedArgs > 0) {
					print.accept(", ");
				}
				print.accept("(" + refs.get(i).name +")");
				print.accept("null");
			}
			println.accept(");");
			println.accept("  }");
		}

		print.accept("  public " + tname + "(");
		int numAddedArgs = 0;
		for (int i = 0; i < fields.size(); i++) {
			if (refs.get(i).kind.isConstant()) {
				continue;
			}
			if (numAddedArgs > 0) {
				print.accept(", ");
			}
			print.accept(refs.get(i).name + " " + fields.get(i).getName());
			++numAddedArgs;
		}
		println.accept(") {");

		for (int i = 0; i < fields.size(); i++) {
			final String name = fields.get(i).getName();
			if (refs.get(i).kind.isConstant()) {
				println.accept("    this." + name + " = \"" + (String) (fields.get(i).get(s)) + "\";");
			} else {
				println.accept("    this." + name + " = " + name + ";");
			}
		}
		println.accept("  }");

		println.accept("  public " + tname + "(java.io.DataInputStream src) throws java.io.IOException {");
		println.accept("    this(new codeprober.protocol.BinaryInputStream.DataInputStreamWrapper(src));");
		println.accept("  }");
		println.accept("  public " + tname + "(codeprober.protocol.BinaryInputStream src) throws java.io.IOException {");
		for (int i = 0; i < fields.size(); i++) {
			final GeneratedType ref = refs.get(i);
			final String name = fields.get(i).getName();
			if (ref.kind.isConstant()) {
				println.accept("    this." + name + " = \"" + (String) (fields.get(i).get(s)) + "\";");
				continue;
			} else {
				println.accept("    this." + name +" = " + ref.genReadFromDataStream.apply("src", "UNUSED") +";");
			}
//			if (numAddedArgs > 0) {
//				print.accept(", ");
//			}
//			print.accept(refs.get(i).name + " " + fields.get(i).getName());
//			++numAddedArgs;
		}
//		println.accept(" {");

//		for (int i = 0; i < fields.size(); i++) {
//			final String name = fields.get(i).getName();
//			if (refs.get(i).kind.isConstant()) {
//				println.accept("    this." + name + " = \"" + (String) (fields.get(i).get(s)) + "\";");
//			} else {
//				println.accept("    this." + name + " = " + name + ";");
//			}
//		}
		println.accept("  }");

		println.accept("");
		println.accept("  public static " + tname + " fromJSON(JSONObject obj) {");
		for (int i = 0; i < fields.size(); i++) {
			if (refs.get(i).kind.isConstant()) {
				println.accept("    "
						+ refs.get(i).genReadFromJsonObj.apply("obj", "\"" + fields.get(i).getName() + "\"") + ";");
			}
		}
		println.accept("    return new " + tname + "(");
		numAddedArgs = 0;
		for (int i = 0; i < fields.size(); i++) {
			if (refs.get(i).kind.isConstant()) {
				continue;
			}
			if (numAddedArgs > 0) {
				print.accept("    , ");
			} else {
				print.accept("      ");
			}
			println.accept(refs.get(i).genReadFromJsonObj.apply("obj", "\"" + fields.get(i).getName() + "\""));
			++numAddedArgs;
		}
		println.accept("    );");
		println.accept("  }");

		println.accept("  public JSONObject toJSON() {");
		println.accept("    JSONObject _ret = new JSONObject();");
		for (int i = 0; i < fields.size(); i++) {
			final String field = fields.get(i).getName();
			final String prefix = "    _ret.put(\"" + field + "\", ";
			switch (refs.get(i).kind) {
			case PRIMITIVE:
			case STRING_CONSTANT:
				println.accept(prefix + field + ");");
				break;
			case STREAMABLE:
				println.accept(prefix + field + ".toJSON());");
				break;
			case ENUM:
				println.accept(prefix + field + ".name());");
				break;
			case OPTIONAL_ENUM:
				println.accept("    if (" + field + " != null) _ret.put(\"" + field + "\", " + field + ".name());");
				break;
			case OPTIONAL_PRIMITIVE:
				println.accept("    if (" + field + " != null) _ret.put(\"" + field + "\", " + field + ");");
				break;
			case OPTIONAL_STREAMABLE:
				println.accept("    if (" + field + " != null) _ret.put(\"" + field + "\", " + field + ".toJSON());");
				break;
			case OPTIONAL_LIST_OF_STREAMABLES: {
				final String arrVal = "new org.json.JSONArray(" + field
						+ ".stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList()))";
				println.accept("    if (" + field + " != null) _ret.put(\"" + field + "\", " + arrVal + ");");
				break;
			}
			case LIST_OF_PRIMTIIVES: {
				println.accept(prefix + "new org.json.JSONArray(" + field + "));");
				break;
			}
			case LIST_OF_STREAMABLES: {
//				List<String> l = new ArrayList<>();
//				System.out.println(l.stream().map(x -> x.toString()).collect(Collectors.toList()) );
				println.accept(prefix + "new org.json.JSONArray(" + field
						+ ".stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())));");
				break;
			}
			case OPTIONAL_LIST_OF_PRIMITIVES: {
				println.accept(prefix + "new org.json.JSONArray(" + field + "));");
				break;
			}
			default: {
				throw new Exception("Unknown field kind " + refs.get(i).kind);
			}
			}
//			System.out.println(")");
		}
		println.accept("    return _ret;");
		println.accept("  }");

		println.accept("  public void writeTo(java.io.DataOutputStream dst) throws java.io.IOException {");
		println.accept("    writeTo(new codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper(dst));");
		println.accept("  }");
		println.accept("  public void writeTo(codeprober.protocol.BinaryOutputStream dst) throws java.io.IOException {");
		for (int i = 0; i < fields.size(); i++) {
			final String field = fields.get(i).getName();
			println.accept("    " + refs.get(i).genWriteToDataStream.genWrite("dst", "UNUSED", field));
		}
		println.accept("  }");

		println.accept("}");

		return out.toString();

	}

	private GeneratedType genRef(Object rawRef) throws Exception {
		if (rawRef instanceof Class<?>) {
			Class<?> clazz = (Class<?>) rawRef;
			if (clazz == String.class) {
				return new GeneratedType("String", FieldKind.PRIMITIVE,
						(obj, field) -> obj + ".getString(" + field + ")",
						(obj, field, val) -> String.format("%s.put(%s, %s);", obj, field, val),
						(obj, field) -> obj + ".readUTF()",
						(obj, field, val) -> String.format("%s.writeUTF(%s);", obj, val),
						(a, b) -> String.format("%s.compareTo(%s)", a, b));
			} else if (clazz == Integer.class) {
				return new GeneratedType("int", FieldKind.PRIMITIVE, //
						(obj, field) -> obj + ".getInt(" + field + ")", //
						(obj, field, val) -> String.format("%s.put(%s, %s);", obj, field, val),
						(obj, field) -> obj + ".readInt()",
						(obj, field, val) -> String.format("%s.writeInt(%s);", obj, val),
						(a, b) -> String.format("Integer.compare(%s, %s)", a, b));
			} else if (clazz == Long.class) {
				return new GeneratedType("long", FieldKind.PRIMITIVE, //
						(obj, field) -> obj + ".getLong(" + field + ")", //
						(obj, field, val) -> String.format("%s.put(%s, %s);", obj, field, val),
						(obj, field) -> obj + ".readLong()",
						(obj, field, val) -> String.format("%s.writeLong(%s);", obj, val),
						(a, b) -> String.format("Long.compare(%s, %s)", a, b));
			} else if (clazz == Boolean.class) {
				return new GeneratedType("boolean", FieldKind.PRIMITIVE,
						(obj, field) -> obj + ".getBoolean(" + field + ")", //
						(obj, field, val) -> String.format("%s.put(%s, %s);", obj, field, val),
						(obj, field) -> obj + ".readBoolean()",
						(obj, field, val) -> String.format("%s.writeBoolean(%s);", obj, val),
						(a, b) -> String.format("Boolean.compare(%s, %s)", a, b));
			} else if (clazz == Void.class) {
				System.err.println("?? When is this used?");
				Thread.dumpStack();
				System.exit(1);
				return null;
//				return new GeneratedType("Object", FieldKind.NULLPOINTER,
//						(obj, field) -> "codeprober.util.JsonUtil.requireNull(" + obj + ".get(" + field + "))", //
//						(obj, field, val) -> String.format("%s.put(%s, JSONObject.NULL);", obj, field));
			} else if (clazz == Object.class) {
				System.err.println("?? When is this used?");
				Thread.dumpStack();
				System.exit(1);
				return null;
//				return new GeneratedType("Object", FieldKind.PRIMITIVE, (obj, field) -> obj + ".get(" + field + ")", //
//						(obj, field, val) -> String.format("%s.put(%s, %s);", obj, field, val),
//						(obj, field) -> obj + ".readJSON()",
//						(obj, field, val) -> String.format("%s.writeJSON(%s);", obj, val));
			} else if (clazz == JSONObject.class) {
				return new GeneratedType("org.json.JSONObject", FieldKind.PRIMITIVE,
						(obj, field) -> obj + ".getJSONObject(" + field + ")", //
						(obj, field, val) -> String.format("%s.put(%s, %s);", obj, field, val),
						(obj, field) -> "new org.json.JSONObject(" + obj+ ".readUTF())",
						(obj, field, val) -> String.format("%s.writeUTF(%s.toString());", obj, val),
						(a, b) -> String.format("%s.toString().compareTo(%s.toString())", a, b));
			} else if (clazz.isEnum()) {
				return new GeneratedType(clazz.getName(), FieldKind.ENUM,
						(obj, field) -> clazz.getName() + ".parseFromJson(" + obj + ".getString(" + field + "))", //
						(obj, field, val) -> String.format("%s.put(%s, %s.name());", obj, field, val),
						(obj, field) -> clazz.getName() + ".values()[" + obj + ".readInt()]",
						(obj, field, val) -> String.format("%s.writeInt(%s.ordinal());", obj, val),
						(a, b) -> String.format("Integer.compare(%s.ordinal, %s.ordinal)", a, b));
			} else {
				if (StreamableUnion.class.isAssignableFrom(clazz)) {
					final RequestedType req = new UnionRequestedType((StreamableUnion) clazz.newInstance());
					requestedTypes.add(req);
					return new GeneratedType(req.getTypeName(), FieldKind.STREAMABLE,
							(obj, field) -> req.getTypeName() + ".fromJSON(" + obj + ".getJSONObject(" + field + "))", //
							(obj, field, val) -> String.format("%s.put(%s, %s.toJSON());", obj, field, val),
							(obj, field) -> String.format("new %s(%s)", req.getTypeName(), obj),
							(obj, field, val) -> String.format("%s.writeTo(%s);", val, obj),
							(a, b) -> String.format("%s.compareTo(%s)", a, b));

				} else if (Streamable.class.isAssignableFrom(clazz)) {
					requestedTypes.add(new NormalRequestedType((Streamable) clazz.newInstance()));
					return new GeneratedType(clazz.getSimpleName(), FieldKind.STREAMABLE,
							(obj, field) -> clazz.getSimpleName() + ".fromJSON(" + obj + ".getJSONObject(" + field
									+ "))", //
							(obj, field, val) -> String.format("%s.put(%s, %s.toJSON());", obj, field, val),
							(obj, field) -> String.format("new %s(%s)", clazz.getSimpleName(), obj),
							(obj, field, val) -> String.format("%s.writeTo(%s);", val, obj),
							(a, b) -> String.format("%s.compareTo(%s)", a, b));
				} else {
					throw new Exception("Invalid class " + clazz);
				}

			}
		} else if (rawRef instanceof String) {
			return new GeneratedType("String", FieldKind.STRING_CONSTANT,
					(obj, field) -> "codeprober.util.JsonUtil.requireString(" + obj + ".getString(" + field + "), \""
							+ rawRef + "\")", //
					(obj, field, val) -> String.format("%s.put(%s, %s);", obj, field, rawRef.toString()),
					(obj, field) -> "\"" + rawRef + "\"", (obj, field, val) -> "",
					(a, b) -> String.format("%s.compareTo(%s)", a, b));
//			System.out.print("\"" + val + "\"");
		} else if (rawRef instanceof StreamableUnion) {
			final RequestedType req = new UnionRequestedType((StreamableUnion) rawRef);
			requestedTypes.add(req);
			return new GeneratedType(req.getTypeName(), FieldKind.STREAMABLE,
					(obj, field) -> req.getTypeName() + ".fromJSON(" + obj + ".getJSONObject(" + field + "))", //
					(obj, field, val) -> String.format("%s.put(%s, %s.toJSON());", obj, field, val),
					(obj, field) -> String.format("new %s(%s)", req.getTypeName(), obj),
					(obj, field, val) -> String.format("%s.writeTo(%s);", val, obj),
					(a, b) -> String.format("%s.compareTo(%s)", a, b));

		} else if (rawRef instanceof Streamable) {
			final RequestedType req = new AnonRequestedType((Streamable) rawRef);
			requestedTypes.add(req);
			return new GeneratedType(req.getTypeName(), FieldKind.STREAMABLE,
					(obj, field) -> req.getTypeName() + ".fromJSON(" + obj + ".getJSONObject(" + field + "))", //
					(obj, field, val) -> String.format("%s.put(%s, %s.toJSON());", obj, field, val),
					(obj, field) -> String.format("new %s(%s)", req.getTypeName(), obj),
					(obj, field, val) -> String.format("%s.writeTo(%s);", val, obj),
					(a, b) -> String.format("%s.compareTo(%s)", a, b));

		} else if (rawRef instanceof Optional<?>) {
			// TODO
			final GeneratedType ent = genRef(((Optional<?>) rawRef).get());
//			return new
			FieldKind kind;
			String boxedName;
			String comparator;
			switch (ent.kind) {
			case PRIMITIVE:
				kind = FieldKind.OPTIONAL_PRIMITIVE;
				switch (ent.name) {
				case "int":
					boxedName = "Integer";
					comparator = "Integer.compare(%s, %s)";
					break;
				case "long":
					boxedName = "Long";
					comparator = "Long.compare(%s, %s)";
					break;
				case "boolean":
					boxedName = "Boolean";
					comparator = "Boolean.compare(%s, %s)";
					break;
				case "String":
					boxedName = ent.name;
					comparator = "%s.compareTo(%s)";
					break;
				case "org.json.JSONObject":
					boxedName = ent.name;
					comparator = "%s.toString().compareTo(%s.toString())"; // TODO toString() is not a good comparison technique
					break;
				default: {
					throw new Exception("Unknown primitive name " + ent.name);
				}
				}
				break;
			case STREAMABLE:
				kind = FieldKind.OPTIONAL_STREAMABLE;
				boxedName = ent.name;
				comparator = "%s.compareTo(%s)";
				break;
			case LIST_OF_STREAMABLES:
				kind = FieldKind.OPTIONAL_LIST_OF_STREAMABLES;
				boxedName = ent.name;
				comparator = "%s.compareTo(%s)";
				break;
			case LIST_OF_PRIMTIIVES:
				kind = FieldKind.OPTIONAL_LIST_OF_PRIMITIVES;
				boxedName = ent.name;
				comparator = "codeprober.util.JsonUtil.compareList(%s, %s, (x, y) -> x - y)";
//				System.err.println("Is this needed??");
//				System.exit(1);
				break;
			case ENUM:
				kind = FieldKind.OPTIONAL_ENUM;
				boxedName = ent.name;
				comparator = "Integer.compare(%s.ordinal, %s.ordinal)";
				break;
			default:
				throw new Exception("Illegal optional field kind " + ent.kind);
			}

			return new GeneratedType(boxedName, kind,
					(obj, field) -> obj + ".has(" + field + ") ? (" + ent.genReadFromJsonObj.apply(obj, field)
							+ ") : null", //
					(obj, field, val) -> {
						return String.format("if (%s != null) %s;", val, ent.genWriteToJson.genWrite(obj, field, val));
					},
					(obj, field) -> obj + ".readBoolean() ? " + ent.genReadFromDataStream.apply(obj, field)
							+ " : null", //
					(obj, field, val) -> {
						return String.format("if (%s != null) { %s.writeBoolean(true); %s; } else { %s.writeBoolean(false); }", val,
								obj, ent.genWriteToDataStream.genWrite(obj, field, val), obj);
					},
					(a, b) -> String.format("%s == null && %s == null ? 0 : (%s == null ? -1 : (%s == null ? 1 : " + comparator + "))", a, b, a, b, a, b));
//					(obj, field, val) -> String.format("%s.put(%s, %s == null ? JSONObject.NULL : (%s))", obj, field, val, //
//							String.format("%s", ) //
//							));

//			return new GeneratedType("String", FieldKind.PRIMITIVE, (obj, field) -> obj + ".getString(" + field + ")");

//			System.out.print("undefined | ");
//			genTypescriptRef(prefix, ((Optional<?>) val).get());
		} else if (rawRef instanceof Object[]) {
			final Object[] opt = (Object[]) rawRef;
			if (opt.length <= 1) {
				throw new Exception("Optionals must have at least two entries, got " + opt.length);
			}

//			final boolean isUnionType = opt[0] instanceof Class<?>;
//			if (isUnionType) {
//				final Class<?>[] asClassArr = new Class<?>[opt.length];
//				for (int i = 0; i < opt.length; ++i) {
//					asClassArr[i] = (Class<?>) opt[i];
////					System.out.println("gen new " + asClassArr[i]);
//					genRef(asClassArr[i]);
////					requestedTypes.add(new NormalRequestedType((Streamable) asClassArr[i].newInstance()));
//				}
//				final RequestedType req = new UnionRequestedType(asClassArr);
//				requestedTypes.add(req);
//				return new GeneratedType(req.getTypeName(), FieldKind.STREAMABLE,
//						(obj, field) -> req.getTypeName() + ".fromJSON(" + obj + ".getJSONObject(" + field + "))");
//			}

			final boolean isEnumLike = opt[0] instanceof String;
			if (isEnumLike) {
				final StringBuilder options = new StringBuilder();
				for (int i = 0; i < opt.length; ++i) {
					options.append(", ");
					options.append("\"" + (String) opt[i] + "\"");
				}

				return new GeneratedType("String", FieldKind.PRIMITIVE,
						(obj, field) -> "codeprober.util.JsonUtil.requireString(" + obj + ".getString(" + field + ")"
								+ options + ")",
						(obj, field, val) -> String.format("%s.put(%s, %s);", obj, field, val),
						(obj, field) -> "codeprober.util.JsonUtil.requireString(" + obj + ".readUTF())",
						(obj, field, val) -> String.format("%s.writeUTF(%s);", obj, val),
						(a, b) -> String.format("%s.compareTo(%s)", a, b));
			}
			throw new Exception("Unknown object array type: " + Arrays.toString(opt));

		} else if (rawRef instanceof List<?>) {
			List<?> l = (List<?>) rawRef;
			if (l.size() != 1) {
				throw new Exception("Lists must have a single entry, got " + l.size());
			}
			final GeneratedType ref = genRef(l.get(0));
			FieldKind kind;
			switch (ref.kind) {
			case PRIMITIVE:
				kind = FieldKind.LIST_OF_PRIMTIIVES;
				break;
			case STREAMABLE:
				kind = FieldKind.LIST_OF_STREAMABLES;
				break;
			default:
				throw new Exception("Illegal list field kind " + ref.kind);
			}

			return new GeneratedType("java.util.List<" + ref.getBoxedName() + ">", kind,
					(obj, field) -> "codeprober.util.JsonUtil.<" + ref.getBoxedName() + ">mapArr(" + obj
							+ ".getJSONArray(" + field + "), (arr, idx) -> "
							+ ref.genReadFromJsonObj.apply("arr", "idx") + ")", //
					(obj, field, val) -> {
						String putter;
						if (ref.kind == FieldKind.PRIMITIVE) {
							putter = val;
						} else {
							putter = val
									+ ".stream().<Object>map(x->x.toJSON()).collect(java.util.stream.Collectors.toList())";
						}
						return String.format("%s.put(%s, new org.json.JSONArray(%s));", obj, field, putter);
					},
					(obj, field) -> {
						final String reader = ref.genReadFromDataStream.apply(obj, "UNUSED");
						return "codeprober.util.JsonUtil.<" + ref.getBoxedName() + ">readDataArr(" + obj
								+ ", () -> " + (reader.endsWith(";") ? reader.substring(0, reader.length() - 1) : reader) + ")";
					}, //
					(obj, field, val) -> {
						final String writer = ref.genWriteToDataStream.genWrite(obj, "UNUSED", "ent");
						return "codeprober.util.JsonUtil.<" + ref.getBoxedName() + ">writeDataArr(" + obj + ", " + val + ", ent -> "
								+ (writer.endsWith(";") ? writer.substring(0, writer.length() - 1) : writer) + ");";
					},
					(a, b) -> String.format("%s.compareTo(%s)", a, b));
		} else {
			throw new Exception("TODO encode " + rawRef);
		}

	}

	private static String getAutoGenDisclaimerLine() {
		return "// Automatically generated by " + GenJava.class.getName() + ". DO NOT MODIFY";
	}
}
