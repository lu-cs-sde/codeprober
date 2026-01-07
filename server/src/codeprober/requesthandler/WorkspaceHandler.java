package codeprober.requesthandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import codeprober.protocol.data.FindWorkspaceFilesReq;
import codeprober.protocol.data.FindWorkspaceFilesRes;
import codeprober.protocol.data.GetWorkspaceFileReq;
import codeprober.protocol.data.GetWorkspaceFileRes;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.PutWorkspaceContentReq;
import codeprober.protocol.data.PutWorkspaceContentRes;
import codeprober.protocol.data.PutWorkspaceMetadataReq;
import codeprober.protocol.data.PutWorkspaceMetadataRes;
import codeprober.protocol.data.RenameWorkspacePathReq;
import codeprober.protocol.data.RenameWorkspacePathRes;
import codeprober.protocol.data.UnlinkWorkspacePathReq;
import codeprober.protocol.data.UnlinkWorkspacePathRes;
import codeprober.protocol.data.WorkspaceEntry;
import codeprober.protocol.data.WorkspaceFile;
import codeprober.requesthandler.FuzzyMatcher.ScoredMatch;

public class WorkspaceHandler {

	public static final String METADATA_DIR_NAME = ".cpr";
	private static boolean debugApiFailureReasons = true;

	private static Pattern preCompiledWorkspaceFilePattern;

	public static Pattern getWorkspaceFilePattern() {
		if (preCompiledWorkspaceFilePattern == null) {
			final String custom = System.getProperty("cpr.workspaceFilePattern");
			if (custom == null) {
				return null;
			}
			preCompiledWorkspaceFilePattern = Pattern.compile(custom);
		}
		return preCompiledWorkspaceFilePattern;
	}

	private static Pattern preCompiledWorkspaceReadOnlyPattern;

	public static Pattern getWorkspaceReadOnlyPattern() {
		if (preCompiledWorkspaceReadOnlyPattern == null) {
			final String custom = System.getProperty("cpr.workspaceReadOnlyPattern");
			if (custom == null) {
				return null;
			}
			preCompiledWorkspaceReadOnlyPattern = Pattern.compile(custom);
		}
		return preCompiledWorkspaceReadOnlyPattern;
	}

	public static boolean supportsStoringWorkspaceMetadata() {
		return "true".equals(System.getProperty("cpr.storeWorkspaceMetadata", "true"));
	}

	private File workspaceRoot;

	public WorkspaceHandler(File workspaceRoot) {
		this.workspaceRoot = workspaceRoot;
	}

	public WorkspaceHandler() {
		this(getWorkspaceRoot(false));
	}

	private static WorkspaceHandler sInstance = new WorkspaceHandler();

	public static void setDefaultWorkspaceHandlerForTesting(WorkspaceHandler wh) {
		sInstance = wh;
	}

	public static WorkspaceHandler getDefault() {
		return sInstance;
	}

	public File relativePathToFile(String path) {
		if (workspaceRoot == null) {
			if (debugApiFailureReasons) {
				System.out.println("No workspace configured");
			}
			return null;
		}
		if (path == null || path.contains("..")) {
			// Missing path, or trying to write outside the workspace dir, not legal.
			if (debugApiFailureReasons) {
				System.out.println("Invalid path: '" + path + "'");
			}
			return null;
		}
		if (File.separatorChar != '/') {
			// Windows, need to replace with backslash
			path = path.replace('/', File.separatorChar);
		}
		return new File(workspaceRoot, path);
	}

	private static File getMetadataFileForRealFile(File realFile) {
		return new File(new File(realFile.getParentFile(), METADATA_DIR_NAME), realFile.getName());
	}

	private static void recursiveRemove(File file, Path boundingDir) {
		if (!file.toPath().startsWith(boundingDir)) {
			// Followed symlink outside of the dir. Could accidentally delete too many
			// files. Stop here
			return;
		}
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					recursiveRemove(child, boundingDir);
				}
			}
		}
		file.delete();
	}

	public List<WorkspaceEntry> listWorkspaceFiles(final File srcDir) {
		final List<WorkspaceEntry> ret = new ArrayList<>();
		if (workspaceRoot != null) {
			final Pattern pattern = getWorkspaceFilePattern();
			final File[] children = srcDir.listFiles();
			if (children != null) {
				Arrays.sort(children, (a, b) -> a.getName().compareTo(b.getName()));
				for (File child : children) {
					if (child.isDirectory() && child.getName().equals(".cpr")) {
						// Metadata directory, hide it
						continue;
					}
					if (child.isFile() && pattern != null) {
						if (!pattern.matcher(relativize(child)).matches()) {
							continue;
						}
					}
					final Pattern readOnlyPattern = getWorkspaceReadOnlyPattern();
					final boolean isReadOnly = readOnlyPattern != null
							&& readOnlyPattern.matcher(relativize(child)).matches();

					ret.add(child.isFile() //
							? WorkspaceEntry.fromFile(new WorkspaceFile(child.getName(), isReadOnly ? true : null))
							: WorkspaceEntry.fromDirectory(child.getName()));
				}
			}
		}
		return ret;
	}

	private String relativize(File workspaceFile) {
		return workspaceRoot.toPath().relativize(workspaceFile.toPath()).toString();
	}

	public static final String WORKSPACE_SYSTEM_PROPERTY_KEY = "cpr.workspace";

	public static File getWorkspaceRoot(boolean exitOnBadConfig) {
		final String workspaceRootCfg = System.getProperty(WORKSPACE_SYSTEM_PROPERTY_KEY);
		if (workspaceRootCfg == null) {
			return null;
		}

		final File workspaceRootFile = new File(workspaceRootCfg).getAbsoluteFile();
		if (workspaceRootFile.isFile()) {
			System.err.println("ERROR: specified 'cpr.workspace' path '" + workspaceRootCfg + "' is a file");
			if (exitOnBadConfig) {
				System.exit(1);
			}
		}
		if (!workspaceRootFile.exists()) {
			if (workspaceRootFile.getParentFile() == null || !workspaceRootFile.getParentFile().exists()) {
				// If both the parent and child path are missing, there is a decent chance of
				// misconfiguration. Print an error and exit/return.
				System.err.println("ERROR: neither the 'cpr.workspace' path '" + workspaceRootCfg
						+ "' nor its parent path exists. Please create the directories before starting CodeProber.");
				if (exitOnBadConfig) {
					System.exit(1);
				}
				return null;
			}
			// Else, only the child path does not exist. Create it
			System.out
					.println("'cpr.workspace' path '" + workspaceRootCfg + "' is missing. Creating an empty directory");
			workspaceRootFile.mkdir();
		}
		return workspaceRootFile;
	}

	public File getWorkspaceFile(String path) {
		final File subFile = relativePathToFile(path);
		if (subFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("Invalid workspace file path");
			}
			return null;
		}
		if (subFile.isDirectory()) {
			if (debugApiFailureReasons) {
				System.out.println("Path is a directory");
			}
			return null;
		}

		return subFile;
	}

	public GetWorkspaceFileRes handleGetWorkspaceFile(GetWorkspaceFileReq req) {

		final File subFile = getWorkspaceFile(req.path);
		if (subFile == null) {
			return new GetWorkspaceFileRes();
		}
		if (!subFile.exists()) {
			if (debugApiFailureReasons) {
				System.out.println("Path is not a file");
			}
			return new GetWorkspaceFileRes();
		}
		try {
			final byte[] textBytes = Files.readAllBytes(subFile.toPath());
			JSONObject metadata = null;
			if (supportsStoringWorkspaceMetadata() && req.loadMeta != null && req.loadMeta) {
				final File metadataFile = getMetadataFileForRealFile(subFile);
				if (metadataFile.exists()) {
					final byte[] metadataBytes = Files.readAllBytes(metadataFile.toPath());
					metadata = new JSONObject(
							new String(metadataBytes, 0, metadataBytes.length, StandardCharsets.UTF_8));
				}
			}
			return new GetWorkspaceFileRes(new String(textBytes, 0, textBytes.length, StandardCharsets.UTF_8),
					metadata);
		} catch (IOException | JSONException e) {
			System.out.println("Error when reading workspace file and/or its accompanying metadata entry");
			return new GetWorkspaceFileRes();
		}
	}

	public ListWorkspaceDirectoryRes handleListWorkspaceDirectory(ListWorkspaceDirectoryReq req) {
		if (workspaceRoot == null) {
			return new ListWorkspaceDirectoryRes();
		}
		if (req.path == null) {
			return new ListWorkspaceDirectoryRes(listWorkspaceFiles(workspaceRoot));
		}
		final File subFile = relativePathToFile(req.path);
		if (subFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("Invalid list path");
			}
			return new ListWorkspaceDirectoryRes();
		}
		if (!subFile.exists() || !subFile.isDirectory()) {
			if (debugApiFailureReasons) {
				System.out.println("Path is not a directory");
			}
			return new ListWorkspaceDirectoryRes();
		}
		return new ListWorkspaceDirectoryRes(listWorkspaceFiles(subFile));
	}

	public PutWorkspaceContentRes handlePutWorkspaceContent(PutWorkspaceContentReq req) {
		return handlePutWorkspaceContent(req.path, out -> out.write(req.content.getBytes(StandardCharsets.UTF_8)));
	}

	public static interface OutputConsumer {
		void accept(OutputStream oos) throws IOException;
	}

	public PutWorkspaceContentRes handlePutWorkspaceContent(String path, OutputConsumer writeFileContents) {
		final File subFile = getWorkspaceFile(path);
		if (subFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("Invalid putContents path '" + path + "'");
			}
			return new PutWorkspaceContentRes(false);
		}
		final Pattern readOnlyPt = getWorkspaceReadOnlyPattern();
		if (readOnlyPt != null && readOnlyPt.matcher(path).matches()) {
			if (debugApiFailureReasons) {
				System.out.println("Tried writing to file matching read-only pattern, path=" + path);
			}
			return new PutWorkspaceContentRes(false);
		}
		if (subFile.exists() && subFile.isDirectory()) {
			if (debugApiFailureReasons) {
				System.out.println("Tried putContents to a directory");
			}
			return new PutWorkspaceContentRes(false);
		}
		subFile.getParentFile().mkdirs();

		try (FileOutputStream fos = new FileOutputStream(subFile)) {
			writeFileContents.accept(fos);
		} catch (IOException e) {
			System.err.println("Error when writing to workspace file " + subFile);
			e.printStackTrace();
			return new PutWorkspaceContentRes(false);
		}

		// Increment write counter
		final String absPath = subFile.getAbsolutePath();
		synchronized (workspaceFileWriteCounters) {
			final Integer prevWriteCounter = workspaceFileWriteCounters.get(absPath);
			workspaceFileWriteCounters.put(absPath, (prevWriteCounter == null ? -1 : prevWriteCounter) + 1);
		}

		return new PutWorkspaceContentRes(true);
	}

	public PutWorkspaceMetadataRes handlePutWorkspaceMetadata(PutWorkspaceMetadataReq req) {
		return handlePutWorkspaceMetadata(req.path, req.metadata == null ? null
				: out -> out.write(req.metadata.toString().getBytes(StandardCharsets.UTF_8)));
	}

	public PutWorkspaceMetadataRes handlePutWorkspaceMetadata(String path, OutputConsumer writeFileContents) {
		final File subFile = getWorkspaceFile(path);
		if (subFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("Invalid putMetadata path '" + path + "'");
			}
			return new PutWorkspaceMetadataRes(false);
		}
		if (subFile.exists() && subFile.isDirectory()) {
			if (debugApiFailureReasons) {
				System.out.println("Tried putMetadata to a directory");
			}
			return new PutWorkspaceMetadataRes(false);
		}

		if (!subFile.exists()) {
			if (debugApiFailureReasons) {
				System.out.println("Cannot put metadata for nonexisting file");
			}
			return new PutWorkspaceMetadataRes(false);
		}
		final File metadataFile = getMetadataFileForRealFile(subFile);
		final Pattern readOnlyPt = getWorkspaceReadOnlyPattern();
		if (readOnlyPt != null && readOnlyPt.matcher(relativize(subFile)).matches()) {
			if (debugApiFailureReasons) {
				System.out.println("Tried putMetadata wherefor a file that matches the read-only pattern");
			}
			return new PutWorkspaceMetadataRes(false);
		}
		if (writeFileContents == null) {
			metadataFile.delete();
		} else {
			metadataFile.getParentFile().mkdirs();

			try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
				writeFileContents.accept(fos);
			} catch (IOException e) {
				System.err.println("Error when writing to workspace file " + subFile);
				e.printStackTrace();
				return new PutWorkspaceMetadataRes(false);
			}
		}
		return new PutWorkspaceMetadataRes(true);
	}

	public RenameWorkspacePathRes handleRenameWorkspacePath(RenameWorkspacePathReq req) {
		final File srcFile = getWorkspaceFile(req.srcPath);
		final File dstFile = getWorkspaceFile(req.dstPath);
		if (srcFile == null || dstFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("Invalid rename paths. src=" + req.srcPath + ", dst=" + req.dstPath);
			}
			return new RenameWorkspacePathRes(false);
		}
		if (!srcFile.exists()) {
			if (debugApiFailureReasons) {
				System.out.println("Rename src does not exist");
			}
			return new RenameWorkspacePathRes(false);
		}
		if (dstFile.exists()) {
			if (!req.srcPath.equals(req.dstPath)
					&& req.srcPath.toLowerCase(Locale.ENGLISH).equals(req.dstPath.toLowerCase(Locale.ENGLISH))) {
				// Case change. On some file systems (looking at you Apple), the target file is
				// considered to already exist, because of case-insensitivity. Go ahead with the
				// rename anyway.
			} else {
				if (debugApiFailureReasons) {
					System.out.println("Rename dst file already exists");
				}
				return new RenameWorkspacePathRes(false);
			}
		}
		if (!dstFile.getAbsoluteFile().getParentFile().exists()) {
			// Target dir nonexisting
			if (debugApiFailureReasons) {
				System.out.println("Rename dst directory does not exist");
			}
			return new RenameWorkspacePathRes(false);
		}
		final Pattern readOnlyPt = getWorkspaceReadOnlyPattern();
		if (readOnlyPt != null
				&& (readOnlyPt.matcher(req.srcPath).matches() || readOnlyPt.matcher(req.dstPath).matches())) {
			if (debugApiFailureReasons) {
				System.out.println("Tried renaming to or from a read-only matching pattern");
			}
			return new RenameWorkspacePathRes(false);
		}
		srcFile.renameTo(dstFile);

		final File srcMetadataFile = getMetadataFileForRealFile(srcFile);
		final File dstMetadataFile = getMetadataFileForRealFile(dstFile);
		if (srcMetadataFile.exists()) {
			srcMetadataFile.renameTo(dstMetadataFile);
		} else {
			dstMetadataFile.delete();
		}
		return new RenameWorkspacePathRes(true);
	}

	public UnlinkWorkspacePathRes handleUnlinkWorkspacePath(UnlinkWorkspacePathReq req) {
		final File file = relativePathToFile(req.path);
		if (file == null) {
			return new UnlinkWorkspacePathRes(false);
		}
		if (!file.exists()) {
			if (debugApiFailureReasons) {
				System.out.println("Tried removing nonexisting file");
			}
			return new UnlinkWorkspacePathRes(false);
		}
		final Pattern readOnlyPt = getWorkspaceReadOnlyPattern();
		if (readOnlyPt != null && readOnlyPt.matcher(req.path).matches()) {
			if (debugApiFailureReasons) {
				System.out.println("Tried removing read-only matching pattern");
			}
			return new UnlinkWorkspacePathRes(false);
		}

		recursiveRemove(file, workspaceRoot.toPath());
		if (file.exists()) {
			if (debugApiFailureReasons) {
				System.out.println("Failed removing " + file + ", perhaps permission problems?");
			}
			return new UnlinkWorkspacePathRes(false);
		}
		return new UnlinkWorkspacePathRes(true);
	}

	private static Map<String, Integer> workspaceFileWriteCounters = new HashMap<>();

	public static int getWorkspaceFileWriteCounter(File f) {
		final String key = f.getAbsolutePath();
		final Integer ret;
		synchronized (workspaceFileWriteCounters) {
			ret = workspaceFileWriteCounters.get(key);
		}
		return ret == null ? -1 : ret;
	}

	public FindWorkspaceFilesRes handleFindWorkspaceFiles(FindWorkspaceFilesReq req) {
		if (workspaceRoot == null) {
			return new FindWorkspaceFilesRes();
		}

		final List<ScoredMatch> matches = new ArrayList<>();

		final int limitMatches = Integer.parseInt(System.getProperty("cpr.workspaceFindFileLimit", "500"));
		final Path wsrootPath = workspaceRoot.toPath();
		final Pattern pattern = getWorkspaceFilePattern();
		final String query = req.query;
		try {
			Files.walkFileTree(wsrootPath, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult visitFile(Path paramT, BasicFileAttributes paramBasicFileAttributes)
						throws IOException {
					final String relPath = wsrootPath.relativize(paramT).toString();
					if (pattern == null || pattern.matcher(relPath).matches()) {
						final Integer score = FuzzyMatcher.score(query, relPath);
						if (score != null) {
							matches.add(new ScoredMatch(relPath, score));
							if (matches.size() >= limitMatches) {
								return FileVisitResult.TERMINATE;
							}
						}
					}
					return super.visitFile(paramT, paramBasicFileAttributes);
				}
			});
		} catch (IOException e) {
			System.err.println("Error when walking workspace files");
			e.printStackTrace();
			return new FindWorkspaceFilesRes();
		}
		matches.sort((a, b) -> Integer.compare(b.score, a.score));

		// Return sorted paths
		List<String> sortedPaths = new ArrayList<>();
		for (ScoredMatch match : matches) {
			sortedPaths.add(match.path);
		}
		return new FindWorkspaceFilesRes(sortedPaths, matches.size() >= limitMatches);
	}
}
