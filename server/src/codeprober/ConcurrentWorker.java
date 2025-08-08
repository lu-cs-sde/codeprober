package codeprober;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONException;
import org.json.JSONObject;

import codeprober.metaprogramming.StreamInterceptor;
import codeprober.protocol.ClientRequest;
import codeprober.protocol.data.AsyncRpcUpdate;
import codeprober.protocol.data.AsyncRpcUpdateValue;
import codeprober.protocol.data.GetWorkerStatusReq;
import codeprober.protocol.data.GetWorkerStatusRes;
import codeprober.protocol.data.RequestAdapter;
import codeprober.protocol.data.SubmitWorkerTaskReq;
import codeprober.protocol.data.SubmitWorkerTaskRes;
import codeprober.protocol.data.WorkerTaskDone;
import codeprober.rpc.JsonRequestHandler;

public class ConcurrentWorker implements JsonRequestHandler {

	private static enum JobStatus {
		IDLE, RUNNING
	};

	private final Thread workerThread;
	private final Monitor monitor;
//	private final JsonRequestHandler underlyingHandler;

	public ConcurrentWorker(JsonRequestHandler underlyingHandler) {
		monitor = new Monitor();
		workerThread = new Thread(() -> {
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

				try {
					System.out.println("running conc worker.. START");
					CodeProber.flog("🕵️ conc start " + task.jobId);
					final JSONObject result = underlyingHandler.handleRequest(new ClientRequest( //
							task.parsedRequestData.data,
							task.request::sendAsyncResponse, //
							task.request.connectionIsAlive,
							task.request.onDidUpdateWorkspacePath));
					CodeProber.flog("🕵️ conc done " + task.jobId);
					System.out.println("running conc worker.. DONE");

					task.request.sendAsyncResponse(
							new AsyncRpcUpdate(task.jobId, true, AsyncRpcUpdateValue.fromWorkerTaskDone(WorkerTaskDone.fromNormal(result)) //
					));
				} catch (Throwable t) {
					CodeProber.flog("🕵 conc caught for " + task.jobId + ": " + t);
					System.out.println("Got throwable: " + t);
					final List<String> stackTrace = new ArrayList<>();
					stackTrace.add(t.toString());
					t.printStackTrace(new StreamInterceptor(System.err, false) {

						@Override
						protected void onLine(String line) {
							stackTrace.add(line);
							CodeProber.flog(line);
						}
					});
					task.request.sendAsyncResponse(
							new AsyncRpcUpdate(task.jobId, true, AsyncRpcUpdateValue.fromWorkerTaskDone(WorkerTaskDone.fromUnexpectedError(stackTrace)) //
					));

//					task.request.sendAsyncResponse(
//							new AsyncRpcUpdate(task.jobId, true, AsyncRpcUpdateValue.fromTaskDone(result) //
//					));
//					throw t;
				} finally {
					monitor.status.set(JobStatus.IDLE);
				}
			}
		});
		workerThread.start();
	}

	@Override
	public JSONObject handleRequest(ClientRequest request) {
		System.out.println("ConcurrentWorker :: handleRequest");
		CodeProber.flog("Worker :: handle " + request.data);

		final JSONObject handled = new RequestAdapter() {

			@Override
			protected GetWorkerStatusRes handleGetWorkerStatus(GetWorkerStatusReq req) {
				final List<String> stackTrace = new ArrayList<>();
				for (StackTraceElement ste : workerThread.getStackTrace()) {
					if (ste.getClassName().equals(DefaultRequestHandler.class.getName())) {
						stackTrace.add("...codeprober internals..");
						break;
					}
					stackTrace.add(ste.toString());
				}
				return new GetWorkerStatusRes(stackTrace);
			}

			@Override
			protected SubmitWorkerTaskRes handleSubmitWorkerTask(SubmitWorkerTaskReq req) {
				try {
					monitor.submit(new Job(req.job, request, req));
					return new SubmitWorkerTaskRes(true);
				} catch (InterruptedException e) {
					System.out.println("Failed to submit");
					e.printStackTrace();
					return new SubmitWorkerTaskRes(false);
				}
			}
		}.handle(request.data); // TODO remove this wrapper
		if (handled == null) {
			throw new JSONException("Unknown request type on " + request.data);
		}
		return handled;

//		switch (request.data.getString("type")) {
//		case "concurrent:pollStatus": {
//			JSONObject response = new JSONObject();
////			final JobStatus id = jobStatuses.get(queryObj.get("job"));
//			response.put("status", monitor.status.get().name());
//			JSONArray stack = new JSONArray();
//			for (StackTraceElement ste : workerThread.getStackTrace()) {
//				if (ste.getClassName().equals(DefaultRequestHandler.class.getName())) {
//					stack.put("...codeprober internals..");
//					break;
//				}
//				stack.put(ste.toString());
//			}
//			response.put("stack", stack);
//			return response;
//		}
//
//		default: {
//			System.err.println("Invalid request " + request.data);
////			final long jobId = jobIdGenerator.getAndIncrement();
////			jobStatuses.put(jobId, JobStatus.SUBMITTED);
////			System.out.println("Got job: " + queryObj);
////			System.out.println("It should contain jobId somewhere");
////			monitor.submit(new Job(jobId, queryObj));
////			break;
//			return null;
//		}
//		}
//		return null;
	}

	private static class Job {
		public final long jobId;
		public final ClientRequest request;
		public final SubmitWorkerTaskReq parsedRequestData;

		public Job(long jobId, ClientRequest request, SubmitWorkerTaskReq parsedRequestData) {
			this.jobId = jobId;
			this.request = request;
			this.parsedRequestData = parsedRequestData;
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
