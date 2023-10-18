package addnum.ast;

public class Diagnostic {
	private final Object humanReadable;
    private final String diagnostic;

    public Diagnostic(Object humanReadable, String diagnostic) {
      this.humanReadable = humanReadable;
      this.diagnostic = diagnostic;
    }

    public Object cpr_getOutput() { return humanReadable; }
    public String cpr_getDiagnostic() { return diagnostic; }

}
