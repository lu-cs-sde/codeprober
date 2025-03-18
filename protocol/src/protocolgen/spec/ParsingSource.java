package protocolgen.spec;

public class ParsingSource extends StreamableUnion {
	public final Object text = String.class;
	public final Object workspacePath = String.class;
}
