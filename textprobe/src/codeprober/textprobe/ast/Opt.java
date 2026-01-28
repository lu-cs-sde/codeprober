package codeprober.textprobe.ast;

public class Opt<T extends ASTNode> extends AbstractASTNode {
  private final T child;
  public Opt() {
    this(null);
  }
  public Opt(T other) {
    super(other == null ? null : other.start, other == null ? null : other.end);
    this.child = other == null ? null : addChild(other);
  }
  public boolean isPresent() {
    return getNumChild() == 1;
  }
  public T get() {
    if (child == null) {
      throw new IllegalStateException("Cannot get a child of an empty Opt");
    }
    return child;
  }
}
