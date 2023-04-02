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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import org.json.JSONException;
import org.json.JSONObject;

import codeprober.rpc.JsonRequestHandler;

public class ConcurrentCoordinator implements JsonRequestHandler {

//	private final AtomicLong jobIdGenerator = new AtomicLong();
	private final AtomicLong messageIdGenerator = new AtomicLong();

	private final Worker[] workers;
	private final ConcurrentLinkedQueue<ActiveJob> queuedJobs = new ConcurrentLinkedQueue<>();
//	private final Function<JSONObject, String> defaultHandler;
	private final JsonRequestHandler nonConcurrentHandler;

	public ConcurrentCoordinator(JsonRequestHandler nonConcurrentHandler, String jarPath, String[] mainArgs,
			Integer workerCount) throws IOException {
		this.nonConcurrentHandler = nonConcurrentHandler;
		workers = new Worker[workerCount != null ? workerCount : 4];
		System.out.println("Starting " + workers.length + " worker process" + (workers.length == 1 ? "" : "es"));
		for (int i = 0; i < workers.length; i++) {
			workers[i] = new Worker(jarPath, mainArgs);
		}
	}

	@Override
	public JSONObject handleRequest(JSONObject queryObj, Consumer<JSONObject> writeAsyncMessage) {
		if (!queryObj.has("job")) {
			return nonConcurrentHandler.handleRequest(queryObj, writeAsyncMessage);
		}

		final ActiveJob job = new ActiveJob(queryObj.getLong("job"), queryObj, writeAsyncMessage);
		final JSONObject result = new JSONObject();
		result.put("job", job.jobId);
		result.put("status", "queued");
		queuedJobs.add(job);

		// TODO possible optimization: first find a worker with equal 'src'/'text' as
		// previous request. Would help caching!
		for (Worker w : workers) {
			synchronized (w) {
				if (w.maybeTakeWork()) {
					System.out.println("Immediate submitted " + job.jobId);
//					w.submit(job);
					return result;
//				result.put("status", "submitted");
//				return result;
//					break;
				}
			}
		}
		CodeProber.flog("Put into queue " + job.jobId);
		System.out.println("No free workers, queuing " + job.jobId);

		return result;
	}

	private static class ActiveJob {
		public final long jobId;
		public final JSONObject config;
		public final Consumer<JSONObject> writeAsyncMessage;

		public ActiveJob(long jobId, JSONObject config, Consumer<JSONObject> writeAsyncMessage) {
			this.jobId = jobId;
			this.config = config;
			this.writeAsyncMessage = writeAsyncMessage;
		}
	}

	private enum CallbackCleanup {
		REMOVE_CALLBACK, KEEP_CALLBACK;
	}

	private class Worker {
		public ActiveJob job;
		public final Process process;
		private final OutputStream outStream;

		private final IpcReader stdoutReader;
		private final List<Thread> threads = new ArrayList<>();

		private Map<Long, Consumer<JSONObject>> rpcHandlers = new ConcurrentHashMap<>();
		private Map<Long, Function<JSONObject, CallbackCleanup>> concurrentUpdateHandlers = new ConcurrentHashMap<>();

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

			stdoutReader = new IpcReader(process.getInputStream()) {
				@Override
				protected void onMessage(String data) {
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
						final Consumer<JSONObject> handler = rpcHandlers.remove(id);
						if (handler == null) {
							System.err.println("Got response for '" + id + "', for which no handler exists");
							return;
						}
						handler.accept(obj);
						break;
					}
					case "concurrent:done": {
						final long jobId = obj.getLong("job");
						final Function<JSONObject, CallbackCleanup> handler = concurrentUpdateHandlers.get(jobId);
						if (handler == null) {
							System.err.println("Got response for job '" + jobId + "', for which no handler exists");
							return;
						}
						final CallbackCleanup cleanup = handler.apply(obj);
						if (cleanup == CallbackCleanup.REMOVE_CALLBACK) {
							concurrentUpdateHandlers.remove(jobId);
						}
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

			threads.add(stdoutThread);
			threads.add(stderrThread);

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				stdoutReader.setSrcWasClosed();
				stderrReader.setSrcWasClosed();
				process.destroy();
				for (Thread t : threads) {
					t.interrupt();
				}
			}));
		}

		public synchronized boolean maybeTakeWork() {
			if (this.job != null) {
				return false;
			}
			final ActiveJob next = queuedJobs.poll();
			if (next != null) {
				submit(next);
				return true;
			}
			return false;
		}

		public synchronized void submit(ActiveJob job) {
			this.job = job;

			final Consumer<JSONObject> dispatchUpdate = result -> {
				final JSONObject msg = new JSONObject();
				msg.put("type", "jobUpdate");
				msg.put("job", job.jobId);
				msg.put("result", result);
				job.writeAsyncMessage.accept(msg);
			};

			final long msgId = messageIdGenerator.getAndIncrement();
			rpcHandlers.put(msgId, resp -> {
				dispatchUpdate.accept(resp.getJSONObject("result"));
			});
			concurrentUpdateHandlers.put(job.jobId, resp -> {
				switch (resp.getString("type")) {
				case "concurrent:done": {
					dispatchUpdate.accept(new JSONObject() //
							.put("status", "done").put("result", resp.getJSONObject("result")) //
					);

					synchronized (Worker.this) {
						this.job = null;
						maybeTakeWork();
					}
					return CallbackCleanup.REMOVE_CALLBACK;
				}

				default: {
					System.err.println("Unknown async message from worker: " + resp);
					return CallbackCleanup.KEEP_CALLBACK;

				}
				}
			});

			final JSONObject jobWrapper = new JSONObject();
			jobWrapper.put("type", "concurrent:submit");
			jobWrapper.put("job", job.jobId);
			jobWrapper.put("id", msgId);
			jobWrapper.put("data", job.config);
			final byte[] msgData = jobWrapper.toString().getBytes(StandardCharsets.UTF_8);
			try {
				outStream.write('\n');
				outStream.write(("<" + msgData.length + ">").getBytes(StandardCharsets.UTF_8));
				outStream.write(msgData);
				outStream.flush();
				CodeProber.flog("Wrote to worker for " + job.jobId);
			} catch (IOException e) {
				CodeProber.flog("error while writing to worker: " + e);
				e.printStackTrace();
				throw new RuntimeException("Sub-process communication failed", e);
			}
			notifyAll();
		}
	}

}
