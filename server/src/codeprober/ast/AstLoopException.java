package codeprober.ast;

/**
 * There are cases where an AST node can have itself as an (indirect) parent.
 * This indicates a bug in the AST construction or how attributes are
 * written.
 */
@SuppressWarnings("serial")
public class AstLoopException extends RuntimeException {

}
