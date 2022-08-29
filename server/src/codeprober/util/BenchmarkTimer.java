package codeprober.util;

public class BenchmarkTimer {

	public static final BenchmarkTimer CREATE_LOCATOR = new BenchmarkTimer();
	public static final BenchmarkTimer APPLY_LOCATOR = new BenchmarkTimer();
	public static final BenchmarkTimer EVALUATE_ATTR = new BenchmarkTimer();
	public static final BenchmarkTimer LIST_NODES = new BenchmarkTimer();
	public static final BenchmarkTimer LIST_PROPERTIES = new BenchmarkTimer();
	
	public static void resetAll() {
		CREATE_LOCATOR.reset();
		APPLY_LOCATOR.reset();
		EVALUATE_ATTR.reset();
		LIST_NODES.reset();
		LIST_PROPERTIES.reset();
	}
	
	private int depth = 0;
	private long accumulated = 0;
	private long start = 0;
	
	
	public void enter() {
		if (depth == 0) {
			start = System.nanoTime();
		}
		++depth;
	}
	public void exit() {
		--depth;
		if (depth == 0) {
			accumulated += System.nanoTime() - start;
		} else if (depth < 0) {
			throw new IllegalStateException("Too many timer.exit() calls");
		}
	}
	
	public void reset() {
		depth = 0;
		accumulated = 0;
	}
	
	public long getAccumulatedNano() {
		if (depth != 0) {
			throw new IllegalStateException("Measurement currently active, make sure to call .enter() & .exit() equal number of times");
		}
		return accumulated;
	}
}
