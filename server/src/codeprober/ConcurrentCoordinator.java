package codeprober;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.json.JSONException;
import org.json.JSONObject;

import codeprober.protocol.ClientRequest;
import codeprober.protocol.data.AsyncRpcUpdate;
import codeprober.protocol.data.AsyncRpcUpdateValue;
import codeprober.protocol.data.EvaluatePropertyReq;
import codeprober.protocol.data.EvaluatePropertyRes;
import codeprober.protocol.data.GetWorkerStatusReq;
import codeprober.protocol.data.GetWorkerStatusRes;
import codeprober.protocol.data.PollWorkerStatusReq;
import codeprober.protocol.data.PollWorkerStatusRes;
import codeprober.protocol.data.PropertyEvaluationResult;
import codeprober.protocol.data.RequestAdapter;
import codeprober.protocol.data.StopJobReq;
import codeprober.protocol.data.StopJobRes;
import codeprober.protocol.data.SubmitWorkerTaskReq;
import codeprober.protocol.data.SubscribeToWorkerStatusReq;
import codeprober.protocol.data.SubscribeToWorkerStatusRes;
import codeprober.protocol.data.TopRequestReq;
import codeprober.protocol.data.TopRequestRes;
import codeprober.protocol.data.UnsubscribeFromWorkerStatusReq;
import codeprober.protocol.data.UnsubscribeFromWorkerStatusRes;
import codeprober.rpc.JsonRequestHandler;

public class ConcurrentCoordinator implements JsonRequestHandler {

//	private final AtomicLong jobIdGenerator = new AtomicLong();
	private final AtomicLong messageIdGenerator = new AtomicLong();

	private final Worker[] workers;
	private final ConcurrentLinkedQueue<ActiveJob> queuedJobs = new ConcurrentLinkedQueue<>();
//	private final Function<JSONObject, String> defaultHandler;
	private final JsonRequestHandler nonConcurrentHandler;

	private final String jarPath;
	private final String[] mainArgs;

	private final AtomicInteger workerStatusSubscriberIdGenerator = new AtomicInteger(1);
	private final CopyOnWriteArrayList<ActiveSubscriber> workerStatusSubscribers = new CopyOnWriteArrayList<>();

	public ConcurrentCoordinator(JsonRequestHandler nonConcurrentHandler, String jarPath, String[] mainArgs,
			Integer workerProcessCount) throws IOException {
		this.jarPath = jarPath;
		this.mainArgs = mainArgs;
		this.nonConcurrentHandler = nonConcurrentHandler;
		workers = new Worker[workerProcessCount != null ? workerProcessCount : 4];
		System.out.println("Starting " + workers.length + " worker process" + (workers.length == 1 ? "" : "es"));
		for (int i = 0; i < workers.length; i++) {
			workers[i] = new Worker(jarPath, mainArgs);
		}
	}

	private void dispatchStatusToSubscribers() {
		if (workerStatusSubscribers.isEmpty()) {
			return;
		}

		final List<String> workerStatuses = new ArrayList<>();
		for (Worker w : workers) {
			// Normally we synchronize on w, but here it doesn't really matter
			// Also it can easily cause deadlocks since this is called from the workers
			// themselves.
//			synchronized (w) {
			if (w.destroyed.get()) {
				workerStatuses.add("Destroyed - failed to replace process");
			} else {

				final ActiveJob job = w.job;
				if (job != null) {
					final String label = job.request.data.optString("jobLabel");
					workerStatuses.add("Working" + (label == null ? "" : (": " + label)));
				} else {
					workerStatuses.add("Idle");
				}

			}
		}
//		final JSONObject status = new JSONObject().put(jarPath, false)

		for (ActiveJob subscriber : workerStatusSubscribers) {
//			final JSONObject msg = new JSONObject();
//			msg.put("type", "jobUpdate");
//			msg.put("job", subscriber.jobId);
//			msg.put("result", new JSONObject() //
//					.put("workers", workerStatuses));

			final AsyncRpcUpdate update = new AsyncRpcUpdate(subscriber.jobId, false,
					AsyncRpcUpdateValue.fromWorkerStatuses(workerStatuses));
			subscriber.request.sendAsyncResponse(update);
		}

	}

	@Override
	public void onOneOrMoreClientsDisconnected() {
		workerStatusSubscribers.removeIf(sub -> !sub.request.connectionIsAlive.get());

		for (int i = 0; i < workers.length; i++) {
			final Worker w = workers[i];
			synchronized (w) {
				if (w.destroyed.get()) {
					continue;
				}
				if (w.job != null && !w.job.request.connectionIsAlive.get()) {
					try {
						workers[i] = new Worker(jarPath, mainArgs);
					} catch (IOException e) {
						System.err.println("Error when replacing worker");
						e.printStackTrace();
						// Continue instead of destroying the previous worker
						// It might not need to be destroyed
						continue;
					}

					w.destroy();
				}
			}
		}
	}

	@Override
	public JSONObject handleRequest(ClientRequest request) {

		try {
			final JSONObject handled = new RequestAdapter() {

				@Override
				protected SubscribeToWorkerStatusRes handleSubscribeToWorkerStatus(SubscribeToWorkerStatusReq req) {
					final int jobId = req.job;
					final int id = workerStatusSubscriberIdGenerator.getAndIncrement();
					workerStatusSubscribers.add(new ActiveSubscriber(jobId, request, id));
					dispatchStatusToSubscribers();
					return new SubscribeToWorkerStatusRes(id);
				}

				@Override
				protected UnsubscribeFromWorkerStatusRes handleUnsubscribeFromWorkerStatus(
						UnsubscribeFromWorkerStatusReq req) {
					final int prevCount = workerStatusSubscribers.size();
					final int subscriberId = req.subscriberId;
					workerStatusSubscribers.removeIf(sub -> sub.subscriberId == subscriberId);
					return new UnsubscribeFromWorkerStatusRes(prevCount != workerStatusSubscribers.size());
				}

				@Override
				protected StopJobRes handleStopJob(StopJobReq req) {
					for (int i = 0; i < workers.length; i++) {
						final Worker w = workers[i];
						synchronized (w) {
							if (w.destroyed.get()) {
								continue;
							}
							if (w.job != null && w.job.jobId == req.job) {
								try {
									workers[i] = new Worker(jarPath, mainArgs);
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
									return new StopJobRes("Failed initializing replacement worker");
								}
								w.destroy();
								dispatchStatusToSubscribers();
								return new StopJobRes(null);
							}
						}
					}
					return new StopJobRes("No such active job");
				}

				@Override
				protected PollWorkerStatusRes handlePollWorkerStatus(PollWorkerStatusReq req) {
					for (Worker w : workers) {
						synchronized (w) {
							if (w.destroyed.get()) {
								continue;
							}
							if (w.job != null && w.job.jobId == req.job) {
								w.pollStack();
								return new PollWorkerStatusRes(true);
							}
						}
					}
					return new PollWorkerStatusRes(false);
				}

				@Override
				protected EvaluatePropertyRes handleEvaluateProperty(EvaluatePropertyReq req) {
					if (req.job == null) {
						// 'Fall down' to nonConcurrent handler below
						throw new JSONException("Synchronous requests not supported");
					}
					final ActiveJob job = new ActiveJob(req.job, request);
//					final JSONObject result = new JSONObject();
//					result.put("job", job.jobId);
//					result.put("status", "queued");
					queuedJobs.add(job);

					final EvaluatePropertyRes result = new EvaluatePropertyRes(
							PropertyEvaluationResult.fromJob(req.job));
					// TODO possible optimization: first find a worker with equal 'src'/'text' as
					// previous request. Would help caching!
					for (Worker w : workers) {
						synchronized (w) {
							if (w.maybeTakeWork()) {
								return result;
							}
						}
					}
					CodeProber.flog("Put into queue " + job.jobId);
					return result;
				}

			}.handle(request.data);
			if (handled != null) {
				return handled;
			}
		} catch (JSONException e) {
			// Not supported and/or not a typed request, fall down
		}

//		if (!request.data.has("job")) {
		return nonConcurrentHandler.handleRequest(request);
	}

	private static class ActiveJob {
		public final long jobId;
		public final ClientRequest request;

		public ActiveJob(long jobId, ClientRequest request) {
			this.jobId = jobId;
			this.request = request;
		}
	}

	private static class ActiveSubscriber extends ActiveJob {
		public final int subscriberId;

		public ActiveSubscriber(int jobId, ClientRequest request, int subscriberId) {
			super(jobId, request);
			this.subscriberId = subscriberId;
		}

	}

	private class Worker {
		public ActiveJob job;
		public final Process process;
		private final OutputStream outStream;

		private Map<Long, Consumer<TopRequestRes>> rpcHandlers = new ConcurrentHashMap<>();
		private Map<Long, Consumer<AsyncRpcUpdate>> concurrentUpdateHandlers = new ConcurrentHashMap<>();

		private final Consumer<Boolean> destroyer;
		private final AtomicBoolean destroyed = new AtomicBoolean(); // TODO prevent accepting new jobs when destroyed

		public Worker(String jarPath, String[] args) throws IOException {
			final List<String> cmd = new ArrayList<>();
			cmd.add("java");
			cmd.add("-jar");

			try {
				cmd.add(new File(CodeProber.class.getProtectionDomain().getCodeSource().getLocation().toURI())
						.getPath());
			} catch (URISyntaxException e) {
				e.printStackTrace();
				throw new IOException(e);
			}
			cmd.add("--worker");
			cmd.add(jarPath);
			for (String arg : args) {
				cmd.add(arg);
			}
			process = new ProcessBuilder() //
					.command(cmd) //
					.start();

			outStream = process.getOutputStream();

			final IpcReader stdoutReader = new IpcReader(process.getInputStream()) {
				@Override
				protected void onMessage(String data) {
//					System.out.println("msg from worker: " + data);
					JSONObject obj;
					try {
						obj = new JSONObject(data);
					} catch (JSONException e) {
						System.out.println("Got non-json data from worker: '" + data + "'");
						e.printStackTrace();
						return;
					}

					switch (obj.getString("type")) {
					case "rpc": {
						final long id = obj.getLong("id");
						final Consumer<TopRequestRes> handler = rpcHandlers.remove(id);
						if (handler == null) {
							System.err.println("Got response for '" + id + "', for which no handler exists");
							return;
						}
						handler.accept(TopRequestRes.fromJSON(obj));
						break;
					}

					case "asyncUpdate": {
						final AsyncRpcUpdate update;
						try {
							update = AsyncRpcUpdate.fromJSON(obj);
						} catch (JSONException e) {
							System.out.println("Got non-" + AsyncRpcUpdate.class.getSimpleName()
									+ " json from worker: '" + obj + "'");
							e.printStackTrace();
							return;
						}

//						System.out.println("Got asyncUpdate for " + update.job +", type: " + update.value.type);
						final Consumer<AsyncRpcUpdate> handler = concurrentUpdateHandlers.get(update.job);
						if (handler == null) {
							System.err
									.println("Got response for job '" + update.job + "', for which no handler exists");
							return;
						}
						if (update.isFinalUpdate) {
							concurrentUpdateHandlers.remove(update.job);
						}
						handler.accept(update);
						break;
					}

					default: {
						System.out.println("Got unknown message from worker: " + obj);
						break;
					}

					}
				}
			};
			final Thread stdoutThread = new Thread(stdoutReader::runForever);
			stdoutThread.start();

			final IpcReader stderrReader = new IpcReader(process.getErrorStream()) {
				@Override
				protected void onMessage(String data) {
					System.out.println("Got worker stdErr msg: " + data);
				}
			};
			final Thread stderrThread = new Thread(() -> {
				stderrReader.runForever();
			});
			stderrThread.start();

			final List<Thread> threads = new ArrayList<>();
			threads.add(stdoutThread);
			threads.add(stderrThread);

			destroyer = forcibly -> {
				synchronized (Worker.this) {
					destroyed.set(true);
					stdoutReader.setSrcWasClosed();
					stderrReader.setSrcWasClosed();
					if (forcibly) {
						process.destroyForcibly();
					} else {
						process.destroy();
					}
					for (Thread t : threads) {
						t.interrupt();
					}
				}
			};
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				destroyer.accept(false);
			}));

		}

		public synchronized void destroy() {
			destroyer.accept(true);
		}

		public synchronized void pollStack() {
			final ActiveJob job = this.job;
			final long msgId = messageIdGenerator.getAndIncrement();
			rpcHandlers.put(msgId, rawResp -> {
				final GetWorkerStatusRes resp = GetWorkerStatusRes.fromJSON(rawResp.data.asSuccess());
				dispatchUpdate(job, false, //
						AsyncRpcUpdateValue.fromWorkerStackTrace(resp.stackTrace));
			});
			write(new TopRequestReq(msgId, new GetWorkerStatusReq().toJSON()));
//			write(new GetWorkerStatusReq().toJSON());
		}

		public synchronized boolean maybeTakeWork() {
			if (this.job != null || destroyed.get()) {
				return false;
			}
			final ActiveJob next = queuedJobs.poll();
			if (next != null) {
				submit(next);
				return true;
			}
			return false;
		}

		private void dispatchUpdate(ActiveJob job, boolean isFinalUpdate, AsyncRpcUpdateValue value) {
			job.request.sendAsyncResponse(new AsyncRpcUpdate(job.jobId, isFinalUpdate, value));
		}

		public synchronized void submit(ActiveJob job) {
			this.job = job;
			dispatchStatusToSubscribers();

			final long msgId = messageIdGenerator.getAndIncrement();
			rpcHandlers.put(msgId, resp -> {
				// TODO check this response?
//				System.out.println("Task submission res: " + resp.toJSON());
//				final SubmitWorkerTaskRes res = SubmitWorkerTaskRes.fromJSON(resp.data.asSuccess());
//				dispatchUpdate(job, false, AsyncRpcUpdateValue.fromJSON(resp));
			});
			concurrentUpdateHandlers.put(job.jobId, resp -> {
//				System.out.println("Conc update: " + resp.toJSON() +" --- isFinal: " + resp.isFinalUpdate);
				dispatchUpdate(job, resp.isFinalUpdate, resp.value);
				if (resp.isFinalUpdate) {
					synchronized (this) {
						this.job = null;
					}
					maybeTakeWork();
					dispatchStatusToSubscribers();
				}
			});

			// BEFORE
//			final JSONObject jobWrapper = new JSONObject();
//			jobWrapper.put("type", "concurrent:submit");
//			jobWrapper.put("job", job.jobId);
//			jobWrapper.put("id", msgId);
//			jobWrapper.put("data", job.request.data);
//			write(jobWrapper.toString());


			// NOW
			write(new TopRequestReq(msgId, new SubmitWorkerTaskReq(job.jobId, job.request.data).toJSON()));
		}

		private synchronized void write(TopRequestReq obj) {
			final byte[] msgData = obj.toJSON().toString().getBytes(StandardCharsets.UTF_8);
			try {
				outStream.write('\n');
				outStream.write(("<" + msgData.length + ">").getBytes(StandardCharsets.UTF_8));
				outStream.write(msgData);
				outStream.flush();
//				CodeProber.flog("Wrote to worker for " + job.jobId);
			} catch (IOException e) {
				CodeProber.flog("error while writing to worker: " + e);
				e.printStackTrace();
				throw new RuntimeException("Sub-process communication failed", e);
			}

		}
	}

}
