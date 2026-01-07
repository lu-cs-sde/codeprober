package codeprober.textprobe.ast;

public class Argument extends AbstractASTNode {

	public static enum ArgumentType {
		STRING, INT, QUERY,
	}

	public final ArgumentType type;
	private final Object value;

	public Argument(Label str) {
		this(str.start, str.end, ArgumentType.STRING, str);
		addChild(str);
	}

	public Argument(Position start, Position end, Integer ival) {
		this(start, end, ArgumentType.INT, ival);
	}

	public Argument(Query q) {
		this(q.start, q.end, ArgumentType.QUERY, q);
		addChild(q);
	}

	private Argument(Position start, Position end, ArgumentType type, Object value) {
		super(start, end);
		this.type = type;
		this.value = value;
	}

	public String asString() {
		return ((Label) value).value;
	}

	public Integer asInt() {
		return (int) value;
	}

	public Query asQuery() {
		return (Query) value;
	}

	@Override
	public String pp() {
		switch (type) {
		case STRING:
			return "\"" + asString() + "\"";
		case INT:
			return String.valueOf(asInt());
		case QUERY:
			return asQuery().pp();
		default:
			return "?";
		}
	}
}
