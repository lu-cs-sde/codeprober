package codeprober.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DirectoryMontorTest {

	private File tmpDir;

	@Before
	public void setup() {
		try {
			tmpDir = File.createTempFile("cpr_test", "tmp");
		} catch (IOException e) {
			throw new RuntimeException("Failed creating tmpDir for test", e);
		}
		tmpDir.delete();
		tmpDir.mkdir();
		tmpDir.deleteOnExit();
	}

	@After
	public void cleanup() {
		delete(tmpDir);
	}

	private void delete(File f) {
		if (f.isDirectory()) {
			for (File child : f.listFiles()) {
				delete(child);
			}
		}
		f.delete();
	}

	@Test
	public void testSimple() throws IOException, InterruptedException {
		final ObservableDirMonitor dm = new ObservableDirMonitor(tmpDir);
		dm.startAndWaitForStartup();

		final File file = new File(tmpDir, "myfile");

		// No initial change
		assertEquals(0, dm.changes);

		file.createNewFile();
		dm.waitForChangeCount(1);

		Files.write(file.toPath(), new byte[3], StandardOpenOption.TRUNCATE_EXISTING);
		dm.waitForChangeCount(2);
	}

	@Test
	public void testChangesInNewSubDirectory() throws IOException, InterruptedException {
		final ObservableDirMonitor dm = new ObservableDirMonitor(tmpDir);
		dm.startAndWaitForStartup();

		final File subdir = new File(tmpDir, "subdir");

		// No initial change
		assertEquals(0, dm.changes);

		subdir.mkdirs();
		dm.waitForChangeCount(1);

		final File subdirFile = new File(subdir, "innerfile");
		Files.write(subdirFile.toPath(), new byte[3], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		dm.waitForChangeCount(2);

		Files.write(subdirFile.toPath(), new byte[5], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		dm.waitForChangeCount(3);
	}

	private static class ObservableDirMonitor extends DirectoryMonitor {

		int changes;
		boolean hasRegistered;

		public ObservableDirMonitor(File srcDir) {
			super(srcDir);
		}

		@Override
		protected void registerRecursive(WatchService watchService, Path root) throws IOException {
			super.registerRecursive(watchService, root);
			synchronized (this) {
				hasRegistered = true;
				notifyAll();
			}
		}

		public synchronized void startAndWaitForStartup() throws InterruptedException {
			start();
			while (!hasRegistered) {
				wait();
			}
		}


		@Override
		public void onChange() {
			// Sleep 1ms so that "lastModified" has a chance to update after each change
			try {
				Thread.sleep(1L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			++changes;
			synchronized (this) {
				notifyAll();
			}
		}

		public synchronized void waitForChangeCount(int expectedCount) throws InterruptedException {
			while (changes != expectedCount) {
				wait();
			}
		}
	}
}
