package pasta.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pasta.AstInfo;
import pasta.ast.ASTNodeAnnotation.Attribute;
import pasta.metaprogramming.PositionRepresentation;
import pasta.protocol.PositionRecoveryStrategy;

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

		public Object getChild(int index) {
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

		private Map<Object, Object> parameterizedNTA_int_Node_values = new HashMap<>();
		
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

		@Attribute
		public Object parameterizedNTA(int arg1, Node arg2) {
			final List<Object> argList = new ArrayList<>();
			argList.add(arg1);
			argList.add(arg2);
			return parameterizedNTA_int_Node_values.get(argList);
		}

		public int timesTwo(int v) {
			return v * 2;
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
	public static Object getAmbiguous() {
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

	public static AstInfo getInfo(AstNode root) {
		return new AstInfo(root, PositionRecoveryStrategy.ALTERNATE_PARENT_CHILD, PositionRepresentation.PACKED_BITS,
				cn -> {
					try {
						return Class.forName(cn);
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				});
	}
}
