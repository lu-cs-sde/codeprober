# Text Probes implementation

A Text Probe implementation with a test suite that is written with Text Probes.
This contains a majority of the text probe logic for CodeProber.

The parser and AST structure is handwritten (no parser generator for example).
It relies heavily on a "typed union"-style rather than subtyping, with union classes in the following pattern:

```java
class Choice {
  public enum Type { A, B }

  public final Type type;
  private final Object detail;

  private Choice(Type type, Object detail) { ... }

  public static Choice fromA(A a) { return new Choice(Type.A, a); }
  public static Choice fromB(B b) { return new Choice(Type.B, b); }

  public A asA() { return (A)detail; }
  public A asB() { return (B)detail; }
}
```

With a higher Java version it would be possible to do something prettier. This works, but requires a bit more typing.

The semantics are implemented in with on-demand computation in mind. There are no real phases.
However, some attributes are off-limits if there are semantic errors, like Query.inflate.
If a meta variable is recursively defined then Query.inflate may fail.
Therefore, check Document.problems().isEmpty() before trying to inflate queries or evaluate them.
