package codeprober.textprobe.ast;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ASTList<T extends ASTNode> extends AbstractASTNode implements Iterable<T> {
	public ASTList(Position start, Position end) {
		super(start, end);
	}

	public ASTList() {
		super();
	}

	public T add(T child) {
		return super.addChild(child);
	}

	public void addAll(ASTList<T> src) {
		for (T v : src) {
			add(v);
		}
	}

	public boolean isEmpty() {
		return getNumChild() == 0;
	}

	@SuppressWarnings("unchecked")
	public T get(int idx) {
		return (T) super.getChild(idx);
	}

	@Override
	public Iterator<T> iterator() {
		Iterator<ASTNode> real = super.children.iterator();
		return new Iterator<T>() {
			@Override
			public boolean hasNext() {
				return real.hasNext();
			}

			@Override
			@SuppressWarnings("unchecked")
			public T next() {
				return (T) real.next();
			}
		};
	}

	@SuppressWarnings("unchecked")
	public Stream<T> stream() {
		return ((Collection<T>) super.children).stream();
	}

	public List<T> toList() {
		return stream().collect(Collectors.toList());
	}
}
