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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Like {@link FileMonitor} but for a directory.
 */
public abstract class DirectoryMonitor extends Thread {
	private final File srcDir;
	private AtomicBoolean stop = new AtomicBoolean(false);
	private Map<Path, Long> fileLastModifieds = new HashMap<>();
	private Map<Path, String> directoryLastListings = new HashMap<>();
	public boolean verboseDebug = false;

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

	protected void onChangeDetected(Path path) {
		/* Default: noop */
	}

	public abstract void onChange();

	protected void registerRecursive(WatchService watchService, final Path root) throws IOException {
		// register all subfolders
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult visitFile(Path paramT, BasicFileAttributes paramBasicFileAttributes)
					throws IOException {
				fileLastModifieds.put(paramT, paramT.toFile().lastModified());
				return super.visitFile(paramT, paramBasicFileAttributes);
			}

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
						StandardWatchEventKinds.ENTRY_MODIFY);
				directoryLastListings.put(dir, createDirListing(dir));
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private String createDirListing(Path dir) {
		final File[] files = dir.toFile().listFiles();
		if (files == null) {
			System.out.println("Failed listing dir " + dir);
			return "";
		}
		return Arrays.asList(files).stream().map(x -> x.getName()).sorted().collect(Collectors.toList()) + "";
	}

	private boolean pollForChanges(WatchService watchService) {
		final AtomicBoolean didChange = new AtomicBoolean();

		try {
			Files.walkFileTree(srcDir.toPath(), new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					long lm = file.toFile().lastModified();
					Long prev = fileLastModifieds.get(file);
					if (prev == null || prev != lm) {
						fileLastModifieds.put(file, lm);
						onChangeDetected(file);
						didChange.set(true);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					final String lastListing = directoryLastListings.get(dir);
					if (lastListing == null) {
						// This directory didn't exist before. A change was already detected by our
						// parent dir. No need to visit the subtree under this. Just register for future
						// changes and move on.
						registerRecursive(watchService, dir);
						return FileVisitResult.SKIP_SUBTREE;
					}
					final String newListing = createDirListing(dir);
					if (!newListing.equals(lastListing)) {
						directoryLastListings.put(dir, newListing);
						onChangeDetected(dir);
						didChange.set(true);
					}
					return super.preVisitDirectory(dir, attrs);
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) {
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			System.err.println("IO Error when walking directory " + srcDir);
			e.printStackTrace();
		}
		return didChange.get();
	}

	@Override
	public void run() {
		try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
			registerRecursive(watcher, srcDir.toPath());
			while (!isStopped()) {
				WatchKey key;
				try {
					if (verboseDebug) {
						System.out.println("Polling...");
					}
					key = watcher.poll(500, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					if (verboseDebug) {
						System.out.println("Interrupted!");
					}
					return;
				}
				if (key == null) {
					Thread.yield();
					continue;
				}

				for (WatchEvent<?> event : key.pollEvents()) {
					WatchEvent.Kind<?> kind = event.kind();
					if (verboseDebug) {
						System.out.println("Polled event: " + kind);
					}

					boolean didChange;
					if (kind == StandardWatchEventKinds.OVERFLOW) {
						System.out.println("Directory monitor event overflow");
						Thread.yield();
						continue;
					} else {
						if (verboseDebug) {
							System.out.println("Polling for changes..");
						}
						didChange = pollForChanges(watcher);
					}
					boolean valid = key.reset();
					if (didChange) {
						if (verboseDebug) {
							System.out.println("Detected change!");
						}
						try {
							onChange();
						} catch (RuntimeException e) {
							System.err.println("Error in DirectoryMonitor.onChange callback");
							e.printStackTrace();
						}
					}
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
