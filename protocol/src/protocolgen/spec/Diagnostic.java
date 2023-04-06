package protocolgen.spec;

import codeprober.protocol.DiagnosticType;

public class Diagnostic extends Streamable {
	public final Object type = DiagnosticType.class;
	public final Object start = Integer.class;
	public final Object end = Integer.class;
	public final Object msg = String.class;
}