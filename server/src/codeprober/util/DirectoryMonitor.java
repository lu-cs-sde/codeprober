package codeprober.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Like {@link FileMonitor} but for a directory.
 */
public abstract class DirectoryMonitor extends Thread {
	private final File srcDir;
	private AtomicBoolean stop = new AtomicBoolean(false);
	private Map<Path, Long> lastModifieds = new HashMap<>();

	public DirectoryMonitor(File srcDir) {
		this.srcDir = srcDir;
		setPriority(Thread.MAX_PRIORITY);
	}

	public boolean isStopped() {
		return stop.get();
	}

	public void stopThread() {
		stop.set(true);
	}

	public abstract void onChange();

	private void registerRecursive(WatchService watchService, final Path root) throws IOException {
		// register all subfolders
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
						StandardWatchEventKinds.ENTRY_MODIFY);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void pollForChanges() throws IOException {
		final AtomicBoolean didChange = new AtomicBoolean();

		Files.walkFileTree(srcDir.toPath(), new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				long lm = file.toFile().lastModified();
				Long prev = lastModifieds.get(file);
				if (prev == null || prev != lm) {
					lastModifieds.put(file, lm);
					didChange.set(true);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		if (didChange.get()) {
			try {
				onChange();
			} catch (RuntimeException e) {
				System.err.println("Error in DirectoryMonitor.onChange callback");
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {
		try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
			registerRecursive(watcher, srcDir.toPath());
			while (!isStopped()) {
				WatchKey key;
				try {
					key = watcher.poll(1000, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					return;
				}
				if (key == null) {
					Thread.yield();
					continue;
				}

				for (WatchEvent<?> event : key.pollEvents()) {
					WatchEvent.Kind<?> kind = event.kind();

					if (kind == StandardWatchEventKinds.OVERFLOW) {
						Thread.yield();
						continue;
					} else {
						pollForChanges();
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
