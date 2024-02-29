package codeprober.ast;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import codeprober.AstInfo;
import codeprober.ast.ASTNodeAnnotation.Attribute;
import codeprober.metaprogramming.AstNodeApiStyle;
import codeprober.metaprogramming.TypeIdentificationStyle;
import codeprober.protocol.PositionRecoveryStrategy;

public class TestData {

	public static class Node {
		private final int start;
		private final int end;
		private final List<Node> children = new ArrayList<>();
		protected Node parent = null;

		public Node(int start, int end) {
			this.start = start;
			this.end = end;
		}

		public Node add(Node child) {
			children.add(child);
			child.parent = this;
			return this;
		}

		public int getNumChild() {
			return children.size();
		}

		public Node getChild(int index) {
			return children.get(index);
		}

		public int getStart() {
			return start;
		}

		public int getEnd() {
			return end;
		}

		public Object getParent() {
			return parent;
		}

		public void mthWithPrintStreamArg(PrintStream ps) {
			ps.println("First msg with linebreak");
			ps.print("Second msg without linebreak");
		}

		private Object simpleNTA_value;

		public Node setSimpleNta(Node value) {
			simpleNTA_value = value;
			value.parent = this;
			return this;
		}

		@Attribute
		public Object simpleNTA() {
			return simpleNTA_value;
		}

		private Map<Object, Node> parameterizedNTA_int_Node_values = new HashMap<>();

		@SuppressWarnings("unused")
		private final Object parameterizedNTA_int_Node_proxy = new Object();

		public Node setParameterizedNTA(int arg1, Node arg2, Node value) {
			final List<Object> argList = new ArrayList<>();
			argList.add(arg1);
			argList.add(arg2);
			parameterizedNTA_int_Node_values.put(argList, value);

			// Emulate "proxy" behavior from JastAdd
			// rather than setting parent directly on the value.
			final Node proxy = new Node(lc(0, 0), lc(0, 0));
			proxy.parent = this;
			value.parent = proxy;

			return this;
		}

		private Node onDemandNTA_value;

		@Attribute
		public Node onDemandNTA() {
			// This println is used for test assertions, do not remove it
			System.out.println("Calling onDemandNTA");
			if (onDemandNTA_value == null) {
				onDemandNTA_value = new Foo(0, 0).add(new Bar(0, 0));
				onDemandNTA_value.parent = this;
			}
			return onDemandNTA_value;
		}

		public void flushTreeCache() {
			onDemandNTA_value = null;
			for (Node n : children) {
				n.flushTreeCache();
			}
		}

		@Attribute
		public Node parameterizedNTA(int arg1, Node arg2) {
			final List<Object> argList = new ArrayList<>();
			argList.add(arg1);
			argList.add(arg2);
			return parameterizedNTA_int_Node_values.get(argList);
		}

		public void cpr_setTraceReceiver(Consumer<Object[]> recv) {
			// No-op
		}

		public int timesTwo(int v) {
			return v * 2;
		}

		public Object cpr_lInvoke(String prop) {
			return (getClass().getSimpleName() + ":" + prop).hashCode();
		}

		@SuppressWarnings("serial")
		public int throwRuntimeException() {
			throw new RuntimeException("Simulated failure") {
				@Override
				public void printStackTrace() {
					// Noop to avoid cluttering the test logs with this
				}
			};
		}
	}

	public static class Program extends Node {
		public Program(int start, int end) {
			super(start, end);
		}
	}

	public static class Foo extends Node {
		public Foo(int start, int end) {
			super(start, end);
		}
	}

	public static class Bar extends Node {
		public Bar(int start, int end) {
			super(start, end);
		}
	}

	public static class Baz extends Node {
		public Baz(int start, int end) {
			super(start, end);
		}
	}

	public static class Qux extends Node {
		public Qux(int start, int end) {
			super(start, end);
		}

		public boolean cpr_isInsideExternalFile() {
			return true;
		}
	}

	public static class Labeled extends Node {
		private String label;

		public Labeled(int start, int end, String label) {
			super(start, end);
			this.label = label;
		}

		public String cpr_nodeLabel() {
			return label;
		}

	}

	/**
	 * Supports both {@link AstNodeApiStyle#CPR_SEPARATE_LINE_COLUMN} and
	 * {@link AstNodeApiStyle#JASTADD_SEPARATE_LINE_COLUMN}.
	 */
	public static class WithTwoLineVariants extends Node {
		private Integer cprVal;
		private Integer noPrefixVal;

		public WithTwoLineVariants(Integer cprVal, Integer noPrefixVal) {
			super(0, 0);
			this.cprVal = cprVal;
			this.noPrefixVal = noPrefixVal;
		}

		public Integer cpr_getStartLine() {
			return cprVal;
		}

		public Integer cpr_getStartColumn() {
			return cprVal;
		}

		public Integer cpr_getEndLine() {
			return cprVal;
		}

		public Integer cpr_getEndColumn() {
			return cprVal;
		}

		public Integer getStartLine() {
			return noPrefixVal;
		}

		public Integer getStartColumn() {
			return noPrefixVal;
		}

		public Integer getEndLine() {
			return noPrefixVal;
		}

		public Integer getEndColumn() {
			return noPrefixVal;
		}
	}

	private static int lc(int line, int col) {
		return (line << 12) + col;
	}

	/**
	 * Get an AST looking like this:
	 *
	 * <pre>
	 * 0 Program {
	 * 1   Foo {
	 * 2        Bar {   }
	 * 3   }
	 * 4   Baz {           }
	 * 5 }
	 * </pre>
	 */
	public static Object getSimple() {
		return new Program(lc(0, 0), lc(5, 0)) //
				.add(new Foo(lc(1, 2), lc(3, 2)) //
						.add(new Bar(lc(2, 8), lc(2, 16))) //
				) //
				.add(new Baz(lc(4, 2), lc(4, 18)));
	}

	/**
	 * Get an AST looking like this:
	 *
	 * <pre>
	 * 0 Program {
	 * 1   Foo {
	 * 2     Bar {   }
	 * 2     Bar {   }
	 * 3   }
	 * 5 }
	 * </pre>
	 */
	public static Object getFlatAmbiguous() {
		return new Program(lc(0, 0), lc(4, 0)) //
				.add(new Foo(lc(1, 2), lc(3, 2)) //
						.add(new Bar(lc(2, 8), lc(2, 8))) //
						.add(new Bar(lc(2, 8), lc(2, 8))) //
				);
	}

	/**
	 * Get an AST looking like this:
	 *
	 * <pre>
	 * 0 Program {
	 * 1   Bar {}
	 * 1   Foo {
	 * 1     Bar {   }
	 * 2   }
	 * 3 }
	 * </pre>
	 */
	public static Object getHillyAmbiguous() {
		return new Program(lc(0, 0), lc(3, 0)) //
				.add(new Bar(lc(1, 2), lc(1, 4))) //
				.add(new Foo(lc(1, 2), lc(1, 8)) //
						.add(new Bar(lc(1, 2), lc(1, 8))) //
				);
	}

	/**
	 * Get an AST looking like this:
	 *
	 * <pre>
	 * 0 Program {
	 * 1   Foo {
	 * 1     Bar {
	 * 1       Baz
	 * 1	 }
	 * 1     Bar {   }
	 * 1   }
	 * 1   Foo { }
	 * 3 }
	 * </pre>
	 */
	public static Object getMultipleAmbiguousLevels() {
		return new Program(lc(0, 0), lc(3, 0)) //
				.add(new Foo(lc(1, 2), lc(1, 8)) //
						.add(new Bar(lc(1, 3), lc(1, 7)) //
								.add(new Baz(lc(1, 4), lc(1, 6)))) //
						.add(new Bar(lc(1, 3), lc(1, 7))) //
				) //
				.add(new Foo(lc(1, 2), lc(1, 8))) //
		;
	}

	/**
	 * Get an AST looking like this:
	 *
	 * <pre>
	 * 0 Program {
	 * 1   Foo {
	 * 1     Bar { }
	 * 1   }
	 * 1   Baz {
	 * 1     Bar { }
	 * 1   }
	 * 3 }
	 * </pre>
	 */
	public static Object getIdenticalBarsWithDifferentParents() {
		return new Program(lc(0, 0), lc(3, 0)) //
				.add(new Foo(lc(1, 2), lc(1, 8)) //
						.add(new Bar(lc(1, 3), lc(1, 7))) //
				) //
				.add(new Baz(lc(1, 2), lc(1, 8)) //
						.add(new Bar(lc(1, 3), lc(1, 7)))) //

		;
	}

	/**
	 * Get an AST looking like this:
	 *
	 * <pre>
	 * 0 Program {
	 * 1   Baz {
	 * 1     Foo {
	 * 1       Bar { }
	 * 1     }
	 * 1   }
	 * 1   Qux {
	 * 1     Foo {
	 * 1       Bar { }
	 * 1     }
	 * 1   }
	 * 3 }
	 * </pre>
	 */
	public static Object getIdenticalBarsWithDifferentGrandParents() {
		return new Program(lc(0, 0), lc(3, 0)) //
				.add(new Baz(lc(1, 2), lc(1, 8)) //
						.add(new Foo(lc(1, 3), lc(1, 7)) //
								.add(new Bar(lc(1, 3), lc(1, 7))))) //
				.add(new Qux(lc(1, 2), lc(1, 8)) //
						.add(new Foo(lc(1, 3), lc(1, 7)) //
								.add(new Bar(lc(1, 3), lc(1, 7)))) //
				) //

		;
	}

	/**
	 * Get an AST looking like this:
	 *
	 * <pre>
	 * 0 Program {
	 * 1   nta :: (
	 * 0     Foo {
	 * 0		Bar {   }
	 * 0     	Bar {   }
	 * 0   )
	 * 2 }
	 * </pre>
	 */
	public static Object getWithSimpleNta() {
		return new Program(lc(0, 0), lc(2, 0)) //
				.setSimpleNta(new Foo(lc(0, 0), lc(0, 0)) //
						.add(new Bar(lc(0, 0), lc(0, 0))) //
						.add(new Bar(lc(0, 0), lc(0, 0))) //
				);
	}

	/**
	 * Get an AST looking like this:
	 *
	 * <pre>
	 * 0 Program {
	 * 1   Foo { }
	 * 2   nta(1, Program.child(0)) :: (
	 * 0     Foo {
	 * 0		Bar {   }
	 * 0     	Bar {   }
	 * 0   )
	 * 0   nta(2, Program.child(0)) :: Baz { }
	 * 3 }
	 * </pre>
	 */
	public static Object getWithParameterizedNta() {
		final Foo ntaArg = new Foo(lc(1, 2), lc(1, 4));
		return new Program(lc(0, 0), lc(2, 0)) //
				.add(ntaArg).setParameterizedNTA(1, ntaArg, new Foo(lc(0, 0), lc(0, 0)) //
						.add(new Bar(lc(0, 0), lc(0, 0))) //
						.add(new Bar(lc(0, 0), lc(0, 0))) //
				) //
				.setParameterizedNTA(2, ntaArg, new Baz(lc(0, 0), lc(0, 0)));
	}

	/**
	 * Get an AST looking like this:
	 *
	 * <pre>
	 * 0 Program {
	 * 1   Foo {
	 * 2     Foo { }
	 * 1     nta(1, Program.child(0)) :: (
	 * 1       Foo {
	 * 1         Bar {
	 * 2           Baz {
	* 2             nta(2, Program.child(0)) :: Foo { }
	* 3           }
	 * 3         }
	 * 3     )
	 * 4   }
	 * 5 }
	 * </pre>
	 */
	public static Program getWithNestedParameterizedNta() {
		final Foo ntaArg = new Foo(lc(2, 2), lc(2, 2));
		final Program prog = new Program(lc(1, 1), lc(5, 1));
		prog //
				.add(ntaArg) //
				.setParameterizedNTA(1, ntaArg, new Foo(lc(1, 1), lc(4, 4)) //
						.add(new Bar(lc(1, 1), lc(3, 3)) //
								.add(new Baz(lc(2, 2), lc(3, 3)) //
										.setParameterizedNTA(2, ntaArg, new Foo(lc(1, 1), lc(2, 2)))//
								) //
						) //
				) //
		;
		return prog;
	}

	/**
	 * Get an AST looking like this:
	 *
	 * <pre>
	 * 1 Program {
	 * 0   Foo { }
	 * 3   Foo {
	 * 4     Bar {   }
	 * 5   }
	 * 6 }
	 * </pre>
	 */
	public static Object getAmbiguousUncle() {
		return new Program(lc(0, 0), lc(6, 0)) //
				.add(new Foo(0, 0)) //
				.add(new Foo(lc(3, 2), lc(5, 2)) //
						.add(new Bar(lc(4, 4), lc(4, 5))));
	}

	/**
	 * Get an AST looking like this:
	 *
	 * <pre>
	 * 1 Program {
	 * 1   Labeled[Lbl1] { }
	 * 1   Labeled[Lbl2] { }
	 * 1 }
	 * </pre>
	 */
	public static Object getWithLabels() {
		return new Program(lc(1, 1), lc(1, 1)) //
				.add(new Labeled(lc(1, 1), lc(1, 1), "Lbl1")) //
				.add(new Labeled(lc(1, 1), lc(1, 1), "Lbl2")); //
	}

	public static AstInfo getInfo(AstNode root) {
		return getInfo(root, TypeIdentificationStyle.REFLECTION);
	}

	public static AstInfo getInfo(AstNode root, TypeIdentificationStyle typeIdStyle) {
		return new AstInfo(root, PositionRecoveryStrategy.ALTERNATE_PARENT_CHILD, AstNodeApiStyle.BEAVER_PACKED_BITS,
				typeIdStyle);
	}
}
