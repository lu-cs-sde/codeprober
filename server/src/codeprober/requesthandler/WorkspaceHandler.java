package codeprober.requesthandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONObject;

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

public class WorkspaceHandler {

	public static final String METADATA_DIR_NAME = ".cpr";
	private static boolean debugApiFailureReasons = true;

	private static Pattern getWorkspaceFilePattern() {
		final String custom = System.getProperty("cpr.workspaceFilePattern");
		if (custom == null) {
			return null;
		}
		return Pattern.compile(custom);
	}

	private static File pathToValidFile(File workspaceRoot, String path) {
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

	private static List<WorkspaceEntry> listWorkspaceFiles(final File srcDir) {
		final Pattern pattern = getWorkspaceFilePattern();
		final List<WorkspaceEntry> ret = new ArrayList<>();
		final File[] children = srcDir.listFiles();
		if (children != null) {
			Arrays.sort(children, (a, b) -> a.getName().compareTo(b.getName()));
			for (File child : children) {
				if (child.isDirectory() && child.getName().equals(".cpr")) {
					// Metadata directory, hide it
					continue;
				}
				if (child.isFile() && pattern != null) {
					final String relpath = getWorkspaceRoot(false).toPath().relativize(child.toPath()).toString();
					if (!pattern.matcher(relpath).matches()) {
						continue;
					}
				}
				ret.add(child.isFile() //
						? WorkspaceEntry.fromFile(child.getName())
						: WorkspaceEntry.fromDirectory(child.getName()));
			}
		}
		return ret;
	}

	public static File getWorkspaceRoot(boolean exitOnBadConfig) {
		final String workspaceRootCfg = System.getProperty("cpr.workspace");
		if (workspaceRootCfg == null) {
			return null;
		}

		final File workspaceRootFile = new File(workspaceRootCfg);
		if (!workspaceRootFile.exists()) {
			System.err.println("ERROR: specified 'cpr.workspace' path " + workspaceRootCfg + " does not exist");
			if (exitOnBadConfig) {
				System.exit(1);
			}
		}
		if (!workspaceRootFile.isDirectory()) {
			System.err.println("ERROR: specified 'cpr.workspace' path " + workspaceRootCfg + " is not a directory");
			if (exitOnBadConfig) {
				System.exit(1);
			}
		}
		return workspaceRootFile;
	}

	public static File getWorkspaceFile(String path) {
		final File workspaceRootFile = getWorkspaceRoot(false);
		if (workspaceRootFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("No workspace configured");
			}
			return null;
		}
		final File subFile = pathToValidFile(workspaceRootFile, path);
		if (subFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("Invalid getFile path");
			}
			return null;
		}
		if (!subFile.exists() || subFile.isDirectory()) {
			if (debugApiFailureReasons) {
				System.out.println("Path is not a file");
			}
			return null;
		}
		return subFile;
	}

	public static GetWorkspaceFileRes handleGetWorkspaceFile(GetWorkspaceFileReq req) {
		final File subFile = getWorkspaceFile(req.path);
		if (subFile == null) {
			return new GetWorkspaceFileRes();
		}
		try {
			final byte[] textBytes = Files.readAllBytes(subFile.toPath());
			JSONObject metadata = null;

			final File metadataFile = getMetadataFileForRealFile(subFile);
			if (metadataFile.exists()) {
				final byte[] metadataBytes = Files.readAllBytes(metadataFile.toPath());
				metadata = new JSONObject(new String(metadataBytes, 0, metadataBytes.length, StandardCharsets.UTF_8));
			}
			return new GetWorkspaceFileRes(new String(textBytes, 0, textBytes.length, StandardCharsets.UTF_8),
					metadata);
		} catch (IOException e) {
			System.out.println("Error when reading workspace file and/or its accompanying metadata entry");
			return new GetWorkspaceFileRes();
		}
	}

	public static ListWorkspaceDirectoryRes handleListWorkspaceDirectory(ListWorkspaceDirectoryReq req) {
		final File workspaceRootFile = getWorkspaceRoot(false);
		if (workspaceRootFile == null) {
			return new ListWorkspaceDirectoryRes();
		}
		if (req.path == null) {
			return new ListWorkspaceDirectoryRes(listWorkspaceFiles(workspaceRootFile));
		}
		final File subFile = pathToValidFile(workspaceRootFile, req.path);
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

	public static PutWorkspaceContentRes handlePutWorkspaceContent(PutWorkspaceContentReq req) {
		final File workspaceRootFile = getWorkspaceRoot(false);
		if (workspaceRootFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("No workspace dir configured");
			}
			return new PutWorkspaceContentRes(false);
		}
		final File subFile = pathToValidFile(workspaceRootFile, req.path);
		if (subFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("Invalid putContents path '" + req.path + "'");
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
			fos.write(req.content.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			System.err.println("Error when writing to workspace file " + subFile);
			e.printStackTrace();
			return new PutWorkspaceContentRes(false);
		}

		// Increment write counter
		final String absPath = subFile.getAbsolutePath();
		final Integer prevWriteCounter = workspaceFileWriteCounters.get(absPath);
		workspaceFileWriteCounters.put(absPath, (prevWriteCounter == null ? -1 : prevWriteCounter) + 1);

		return new PutWorkspaceContentRes(true);
	}

	public static PutWorkspaceMetadataRes handlePutWorkspaceMetadata(PutWorkspaceMetadataReq req) {
		final File workspaceRootFile = getWorkspaceRoot(false);
		if (workspaceRootFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("No workspace dir configured");
			}
			return new PutWorkspaceMetadataRes(false);
		}
		final File subFile = pathToValidFile(workspaceRootFile, req.path);
		if (subFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("Invalid putMetadata path '" + req.path + "'");
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
		metadataFile.getParentFile().mkdirs();

		try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
			fos.write(req.metadata.toString().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			System.err.println("Error when writing to workspace file " + subFile);
			e.printStackTrace();
			return new PutWorkspaceMetadataRes(false);
		}
		return new PutWorkspaceMetadataRes(true);
	}

	public static RenameWorkspacePathRes handleRenameWorkspacePath(RenameWorkspacePathReq req) {
		final File workspaceRootFile = getWorkspaceRoot(false);
		if (workspaceRootFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("Workspace not configured");
			}
			return new RenameWorkspacePathRes(false);
		}
		final File srcFile = pathToValidFile(workspaceRootFile, req.srcPath);
		final File dstFile = pathToValidFile(workspaceRootFile, req.dstPath);
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

	public static UnlinkWorkspacePathRes handleUnlinkWorkspacePath(UnlinkWorkspacePathReq req) {
		final File workspaceRootFile = getWorkspaceRoot(false);
		if (workspaceRootFile == null) {
			return new UnlinkWorkspacePathRes(false);
		}
		final File file = pathToValidFile(workspaceRootFile, req.path);
		if (file == null) {
			return new UnlinkWorkspacePathRes(false);
		}
		if (!file.exists()) {
			if (debugApiFailureReasons) {
				System.out.println("Tried removing nonexisting file");
			}
			return new UnlinkWorkspacePathRes(false);
		}
		recursiveRemove(file, workspaceRootFile.toPath());
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
		final Integer ret = workspaceFileWriteCounters.get(key);
		return ret == null ? -1 : ret;
	}

}
