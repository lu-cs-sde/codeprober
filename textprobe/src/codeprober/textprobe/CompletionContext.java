package codeprober.textprobe;

import java.util.Arrays;
import java.util.Objects;

import codeprober.textprobe.ast.ASTNodeAnnotation.Attribute;
import codeprober.textprobe.ast.PropertyAccess;
import codeprober.textprobe.ast.Query;
import codeprober.textprobe.ast.VarDecl;

public class CompletionContext {

	public static enum Type {
		QUERY_HEAD_TYPE, PROPERTY_NAME, QUERY_RESULT, VAR_DECL_NAME;
	}

	public final Type type;
	private final Object detail;

	private CompletionContext(Type type) {
		this(type, null);
	}

	private CompletionContext(Type type, Object detail) {
		this.type = type;
		this.detail = detail;
	}

	public static CompletionContext fromType(Query h) {
		return new CompletionContext(Type.QUERY_HEAD_TYPE, h);
	}

	public static CompletionContext fromPropertyName(Query query, PropertyAccess name) {
		return new CompletionContext(Type.PROPERTY_NAME, new PropertyNameDetail(query, name));
	}

	public static CompletionContext fromQueryResult(Query q) {
		return new CompletionContext(Type.QUERY_RESULT, q);
	}

	public static CompletionContext fromVarDeclName(VarDecl v) {
		return new CompletionContext(Type.VAR_DECL_NAME, v);
	}

	@Attribute
	public Type type() {
		return type;
	}

	@Attribute
	public Query asQueryHead() {
		return (Query) detail;
	}

	@Attribute
	public PropertyNameDetail asPropertyName() {
		return (PropertyNameDetail) detail;
	}

	public Query asQueryResult() {
		return (Query) detail;
	}

	public VarDecl asVarDecl() {
		return (VarDecl) detail;
	}

	public Object cpr_getOutput() {
		return Arrays.asList(type, detail);
	}

	public static class PropertyNameDetail {
		public final Query query;
		public final PropertyAccess access;

		public PropertyNameDetail(Query query, PropertyAccess name) {
			this.query = query;
			this.access = name;
		}

		@Attribute
		public Query query() {
			return query;
		}

		@Attribute
		public PropertyAccess access() {
			return access;
		}

		public Object cpr_getOutput() {
			return Arrays.asList(query, access);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof PropertyNameDetail)) {
				return false;
			}
			final PropertyNameDetail other = (PropertyNameDetail) obj;
			return Objects.equals(query, other.query) && Objects.equals(access, other.access);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof CompletionContext)) {
			return false;
		}
		final CompletionContext other = (CompletionContext) obj;
		return Objects.equals(type, other.type) && Objects.equals(detail, other.detail);
	}
}
