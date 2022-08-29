package codeprober.util;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class FileMonitor extends Thread {
	private final File file;
	private long lastModified;
	private AtomicBoolean stop = new AtomicBoolean(false);

	public FileMonitor(File file) {
		this.file = file.getAbsoluteFile();
		lastModified = file.lastModified();
		setPriority(Thread.MAX_PRIORITY);
	}

	public boolean isStopped() {
		return stop.get();
	}

	public void stopThread() {
		stop.set(true);
	}

	public abstract void onChange();

	private void maybeTriggerDelayedChange() {
		if (!file.exists()) {
			// Might have been removed as part of a clean build.
			return;
		}
		final long currMod = file.lastModified();
		if (currMod == lastModified) {
			// No change
			return;
		}
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			System.out.println("Interrupted while waiting for change to settle");
			e.printStackTrace();
		}
		final long newMod = file.lastModified();
		if (currMod != newMod) {
			// It is being updated right now, wait a little longer
			maybeTriggerDelayedChange();
			return;
		}
		lastModified = newMod;
		onChange();
	}

	@Override
	public void run() {
		try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
			final Path path = file.toPath().getParent();
			path.register(watcher, new WatchEvent.Kind[] { StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_MODIFY });
			while (!isStopped()) {
//            	System.out.println("polling..");
				WatchKey key;
				try {
					key = watcher.poll(1000, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					return;
				}
				if (key == null) {
					maybeTriggerDelayedChange();
					Thread.yield();
					continue;
				}

				for (WatchEvent<?> event : key.pollEvents()) {
					WatchEvent.Kind<?> kind = event.kind();
					System.out.println("got event : " + kind);

					@SuppressWarnings("unchecked")
					WatchEvent<Path> ev = (WatchEvent<Path>) event;
					Path filename = ev.context();

					if (kind == StandardWatchEventKinds.OVERFLOW) {
						System.out.println("overflow!");
						Thread.yield();
						continue;
					} else if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
							&& filename.toString().equals(file.getName())) {
						maybeTriggerDelayedChange();
					}
					boolean valid = key.reset();
					if (!valid) {
						break;
					}
				}
				Thread.yield();
			}
			System.out.println("File monitor stopped");
		} catch (Throwable e) {
			e.printStackTrace();
			// Log or rethrow the error
		}
	}
}
