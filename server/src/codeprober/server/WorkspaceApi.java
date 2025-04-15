package codeprober.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.requesthandler.WorkspaceHandler;

public class WorkspaceApi {

	public interface Responder {
		void respondOK() throws IOException;

		void respondNoContent() throws IOException;

		void respondOK(byte[] contents) throws IOException;

		void respondBadRequest() throws IOException;

		void respondInternalError() throws IOException;

		public static Responder fromOutputStream(OutputStream out) {
			return new Responder() {

				@Override
				public void respondOK() throws IOException {
					WebServer.write200(out);
				}

				@Override
				public void respondNoContent() throws IOException {
					out.write("HTTP/1.1 204 No Content\r\n".getBytes("UTF-8"));
					out.write(("Content-Type: text/plain\r\n").getBytes("UTF-8"));
					out.write(("\r\n").getBytes("UTF-8"));
					out.flush();
				}

				@Override
				public void respondOK(byte[] contents) throws IOException {
					WebServer.write200(out, contents);
				}

				@Override
				public void respondBadRequest() throws IOException {
					WebServer.write400(out);
				}

				@Override
				public void respondInternalError() throws IOException {
					out.write("HTTP/1.1 500 Internal Server Error\r\n".getBytes("UTF-8"));
					out.write(("Content-Type: text/plain\r\n").getBytes("UTF-8"));
					out.write(("\r\n").getBytes("UTF-8"));
					out.flush();
				}
			};
		}
	}

	private static final String METADATA_DIR_NAME = ".cpr";
	private static boolean debugApiFailureReasons = true;
	private final Responder resp;

	private static Pattern getWorkspaceFilePattern() {
		final String custom = System.getProperty("cpr.workspaceFilePattern");
		if (custom == null) {
			return null;
		}
		return Pattern.compile(custom);
	}

	public WorkspaceApi(Responder resp) {
		this.resp = resp;
	}

	private boolean checkValidPath(String path) throws IOException {
		if (path == null || path.contains("..")) {
			// Missing path, or trying to write outside the workspace dir, not legal.
			if (debugApiFailureReasons) {
				System.out.println("Invalid path: '" + path + "'");
			}
			resp.respondBadRequest();
			return false;
		}
		return true;
	}

	private File getMetadataFileForRealFile(File realFile) {
		return new File(new File(realFile.getParentFile(), METADATA_DIR_NAME), realFile.getName());

	}

	public void rename(String srcPath, String dstPath) throws IOException {
		final File workspaceRootFile = WorkspaceHandler.getWorkspaceRoot(false);
		if (workspaceRootFile == null) {
			resp.respondBadRequest();
			return;
		}
		if (!checkValidPath(srcPath) || !checkValidPath(dstPath)) {
			return;
		}
		final File srcFile = new File(workspaceRootFile, srcPath);
		final File dstFile = new File(workspaceRootFile, dstPath);
		if (!srcFile.exists()) {
			if (debugApiFailureReasons) {
				System.out.println("Rename src does not exist");
			}
			resp.respondBadRequest();
			return;
		}
		if (dstFile.exists()) {
			if (debugApiFailureReasons) {
				System.out.println("Rename dst file already exists");
			}
			resp.respondBadRequest();
			return;
		}
		if (!dstFile.getAbsoluteFile().getParentFile().exists()) {
			// Target dir nonexisting
			resp.respondBadRequest();
			if (debugApiFailureReasons) {
				System.out.println("Rename dst directory does not exist");
			}
			return;
		}
		srcFile.renameTo(dstFile);

		final File srcMetadataFile = getMetadataFileForRealFile(srcFile);
		final File dstMetadataFile = getMetadataFileForRealFile(dstFile);
		if (srcMetadataFile.exists()) {
			srcMetadataFile.renameTo(dstMetadataFile);
		} else {
			dstMetadataFile.delete();
		}
		resp.respondOK();
	}

	// This class is easier to test if we don't use the actual Socket class,
	// hence 'SocketLike'
	public interface SocketLike {
		InputStream getInputStream() throws IOException;
	}

	public void putContents(String path, int contentLen, SocketLike body) throws IOException {
		final File workspaceRootFile = WorkspaceHandler.getWorkspaceRoot(false);

		if (workspaceRootFile == null) {
			if (debugApiFailureReasons) {
				System.out.println("No workspace dir configured");
			}
			resp.respondBadRequest();
			return;
		}
		if (!checkValidPath(path)) {
			if (debugApiFailureReasons) {
				System.out.println("Invalid putContents path '" + path +"'");
			}
			return;
		}
		final File file = new File(workspaceRootFile, path);
		if (file.exists() && file.isDirectory()) {
			if (debugApiFailureReasons) {
				System.out.println("Tried putContents to a directory");
			}
			resp.respondBadRequest();
			return;
		}
		file.getParentFile().mkdirs();

		try (FileOutputStream fos = new FileOutputStream(file)) {
			drain(contentLen, body, fos);
		}
		resp.respondOK();
	}

	public void putMetadata(String path, int contentLen, SocketLike body) throws IOException {
		final File workspaceRootFile = WorkspaceHandler.getWorkspaceRoot(false);
		if (workspaceRootFile == null) {
			resp.respondBadRequest();
			return;
		}
		if (!checkValidPath(path)) {
			return;
		}
		final File realFile = new File(workspaceRootFile, path);
		if (!realFile.exists()) {
			// Tried writing metadata for nonexisting file??
			resp.respondBadRequest();
			return;
		}
		if (realFile.isDirectory()) {
			// Cannot write metdata for directories
			resp.respondBadRequest();
			return;
		}
		final File metadataFile = getMetadataFileForRealFile(realFile);
		metadataFile.getParentFile().mkdirs();

		try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
			drain(contentLen, body, fos);
			resp.respondOK();
		}
	}


	public void unlink(String path) throws  IOException {
		final File workspaceRootFile = WorkspaceHandler.getWorkspaceRoot(false);
		if (workspaceRootFile == null) {
			resp.respondBadRequest();
			return;
		}
		if (!checkValidPath(path)) {
			return;
		}
		final File realFile = new File(workspaceRootFile, path);
		if (!realFile.exists()) {
			if (debugApiFailureReasons) {
				System.out.println("Tried removing nonexisting file");
			}
			resp.respondBadRequest();
			return;
		}
		recursiveRemove(realFile, workspaceRootFile.toPath());
		if (realFile.exists()) {
			// Failed somehow
			resp.respondInternalError();
		} else {
			resp.respondOK();
		}
	}

	private void recursiveRemove(File file, Path boundingDir) {
		if (!file.toPath().startsWith(boundingDir)) {
			// Followed symlink outside of the dir. Could accidentally delete too many files. Stop here
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

	private static void drain(int numBytes, SocketLike src, OutputStream dst) throws IOException {
		if (numBytes <= 0) {
			return;
		}
		final InputStream in = src.getInputStream();
		final byte[] buf = new byte[512];
		int read;
		int remaining = numBytes;
		while ((read = in.read(buf)) != -1) {
			dst.write(buf, 0, read);
			remaining -= read;
			if (remaining <= 0) {
				break;
			}
		}
	}

	public void getWorkspace() throws IOException {
		final File workspaceRootFile = WorkspaceHandler.getWorkspaceRoot(false);
		if (workspaceRootFile == null) {
			resp.respondNoContent();
			return;
		}
		resp.respondOK(listWorkspaceFiles(workspaceRootFile).toString().getBytes(StandardCharsets.UTF_8));
	}


	public void getContents(String path) throws IOException {
		final File workspaceRootFile = WorkspaceHandler.getWorkspaceRoot(false);
		if (workspaceRootFile == null) {
			resp.respondBadRequest();
			return;
		}
		if (!checkValidPath(path)) {
			return;
		}
		final File textFile = new File(workspaceRootFile, path);
		if (!textFile.exists()) {
			resp.respondBadRequest();
			return;
		}

		final byte[] respBytes;
		if (textFile.isDirectory()) {
			respBytes = listWorkspaceFiles(textFile).toString().getBytes(StandardCharsets.UTF_8);
		} else {
			final byte[] textBytes = Files.readAllBytes(textFile.toPath());
			final JSONObject writeObj = new JSONObject() //
					.put("text", new String(textBytes, 0, textBytes.length, StandardCharsets.UTF_8));

			final File metadataFile = new File(new File(textFile.getParentFile(), ".cpr"), textFile.getName());
			if (metadataFile.exists()) {
				final byte[] metadataBytes = Files.readAllBytes(metadataFile.toPath());
				writeObj.put("metadata", new String(metadataBytes, 0, metadataBytes.length, StandardCharsets.UTF_8));
			}
			respBytes = writeObj.toString().getBytes(StandardCharsets.UTF_8);
		}
		resp.respondOK(respBytes);
	}

	private JSONObject listWorkspaceFiles(final File srcDir) {
		final Pattern pattern = getWorkspaceFilePattern();
		final JSONArray arr = new JSONArray();
		final File[] children = srcDir.listFiles();
		if (children != null) {
			for (File child : children) {
				if (child.isDirectory() && child.getName().equals(".cpr")) {
					// Metadata directory, hide it
					continue;
				}
				if (child.isFile() && pattern != null && !pattern.matcher(child.getName()).matches()) {
					System.out.println("child " + child.getName() +" does not match");
					continue;
				}
				arr.put(new JSONObject() //
						.put("t", child.isFile() ? "f" : "d") // type (file or dir)
						.put("n", child.getName()) // name
				);
			}
		}
		return new JSONObject() //
				.put("files", arr) //
		;
	}

}
