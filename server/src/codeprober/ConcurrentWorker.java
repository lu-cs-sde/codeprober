package codeprober;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.json.JSONObject;

import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.rpc.JsonRequestHandler;

public class ConcurrentWorker implements JsonRequestHandler {

	private static enum JobStatus {
		IDLE, RUNNING
	};

	private final Thread workerThread;
	private final Monitor monitor;
//	private final JsonRequestHandler underlyingHandler;

	public ConcurrentWorker(JsonRequestHandler underlyingHandler) {
//		this.underlyingHandler = underlyingHandler;
		monitor = new Monitor();
		workerThread = new Thread(() -> {
//			StdIoInterceptor io = new StdIoInterceptor(false) {
//
//				@Override
//				public void onLine(boolean stdout, String line) {
//					CodeProber.flog(line);
//				}
//			};
//			io.install();
			while (true) {
				Job task;
				try {
					System.out.println("workerThread starting take()");
					task = monitor.take();
					System.out.println("workerThread got task: " + task.jobId);
				} catch (InterruptedException e) {
					System.out.println("Worker thread interrupted");
					e.printStackTrace();
					continue; // Or kill thread here?
				}
				monitor.status.set(JobStatus.RUNNING);

//				try {
//					Thread.sleep(1000);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
				try {
//					final JSONObject response = new JSONObject();
//					response.put("type", "status");
//					response.put("id", task.jobId);
					System.out.println("running conc worker.. START");
					CodeProber.flog("🕵️ conc start " + task.jobId);
					final JSONObject result = underlyingHandler.handleRequest(task.config, task.writeAsyncMessage);
					CodeProber.flog("🕵️ conc done " + task.jobId);
					System.out.println("running conc worker.. DONE");
//					response.put("result", result != null ? result : JSONObject.NULL);
//					System.out.println("sending stuff from worker to coordinator: " + response);

					final JSONObject msg = new JSONObject();
					msg.put("type", "concurrent:done");
					msg.put("job", task.jobId);
//					msg.put("data", response);
					msg.put("result", result != null ? result : JSONObject.NULL);
					System.out.println("sending " + msg);
					task.writeAsyncMessage.accept(msg);
				} catch (Throwable t) {
					CodeProber.flog("🕵🕵🕵 conc caught for " + task.jobId);
					System.out.println("Got throwable: " + t);
					throw t;
				} finally {
					monitor.status.set(JobStatus.IDLE);
				}
			}
		});
		workerThread.start();
	}

	@Override
	public JSONObject handleRequest(JSONObject queryObj, Consumer<JSONObject> writeAsyncMessage) {
		System.out.println("ConcurrentWorker :: handleRequest");
		switch (queryObj.getString("type")) {
		case "concurrent:status": {
			JSONObject response = new JSONObject();
//			final JobStatus id = jobStatuses.get(queryObj.get("job"));
			response.put("status", monitor.status.get().name());
			return response;
		}

		case "concurrent:submit": {
			final JSONObject data = queryObj.getJSONObject("data");
			try {
				monitor.submit(new Job(queryObj.getLong("job"), data, writeAsyncMessage));
			} catch (InterruptedException e) {
				System.out.println("Failed to submit");
			}
			return new JSONObject().put("status", "running");
//			return underlyingHandler.handleRequest(data, writeAsyncMessage);
		}

		default: {
			System.err.println("Invalid request " + queryObj);
//			final long jobId = jobIdGenerator.getAndIncrement();
//			jobStatuses.put(jobId, JobStatus.SUBMITTED);
//			System.out.println("Got job: " + queryObj);
//			System.out.println("It should contain jobId somewhere");
//			monitor.submit(new Job(jobId, queryObj));
//			break;
			return null;
		}
		}
//		return null;
	}

	private static class Job {
		public final long jobId;
		public final JSONObject config;
		public final Consumer<JSONObject> writeAsyncMessage;

		public Job(long jobId, JSONObject config, Consumer<JSONObject> writeAsyncMessage) {
			this.jobId = jobId;
			this.config = config;
			this.writeAsyncMessage = writeAsyncMessage;
		}
	}

	private static class Monitor {

		public final AtomicReference<JobStatus> status = new AtomicReference<>(JobStatus.IDLE);

		private Job pending;

		public synchronized Job take() throws InterruptedException {
			while (pending == null) {
				wait();
			}
			final Job ret = pending;
			pending = null;
			notifyAll();
			return ret;
		}

		public synchronized void submit(Job task) throws InterruptedException {
			CodeProber.flog("🕵️ conc submit " + task.jobId + ", hasPending " + (pending != null));
			while (pending != null) {
				wait();
			}
			pending = task;
			notifyAll();
		}
	}
}
