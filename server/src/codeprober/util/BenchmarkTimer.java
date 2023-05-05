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

	private Thread ownerThread;


	public void enter() {
		if (ownerThread != null && Thread.currentThread() != ownerThread) {
			System.err.println("Illegal concurrent timer access to .enter, owned by " + ownerThread + " but accessed by " + Thread.currentThread());
			Thread.dumpStack();
		}
		if (depth == 0) {
			ownerThread = Thread.currentThread();
			start = System.nanoTime();
		}
		++depth;
	}
	public void exit() {
		if (ownerThread != null && Thread.currentThread() != ownerThread) {
			System.err.println("Illegal concurrent timer access to .exit, owned by " + ownerThread + " but accessed by " + Thread.currentThread());
			Thread.dumpStack();
		}
		--depth;
		if (depth == 0) {
			ownerThread = null;
			accumulated += System.nanoTime() - start;
		} else if (depth < 0) {
			throw new IllegalStateException("Too many timer.exit() calls");
		}
	}

	public void reset() {
		if (ownerThread != null && Thread.currentThread() != ownerThread) {
			System.err.println("Illegal concurrent timer access to .reset, owned by " + ownerThread + " but accessed by " + Thread.currentThread());
			Thread.dumpStack();
		}
		depth = 0;
		accumulated = 0;
		ownerThread = null;
	}

	public long getAccumulatedNano() {
		if (depth != 0) {
			throw new IllegalStateException("Measurement currently active, make sure to call .enter() & .exit() equal number of times");
		}
		return accumulated;
	}
}
