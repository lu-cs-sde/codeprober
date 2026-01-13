package codeprober.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.protocol.data.GetWorkspaceFileReq;
import codeprober.protocol.data.GetWorkspaceFileRes;
import codeprober.protocol.data.ListWorkspaceDirectoryReq;
import codeprober.protocol.data.ListWorkspaceDirectoryRes;
import codeprober.protocol.data.PutWorkspaceContentRes;
import codeprober.protocol.data.PutWorkspaceMetadataRes;
import codeprober.protocol.data.RenameWorkspacePathReq;
import codeprober.protocol.data.RenameWorkspacePathRes;
import codeprober.protocol.data.UnlinkWorkspacePathReq;
import codeprober.protocol.data.UnlinkWorkspacePathRes;
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

	private final Responder resp;

	public WorkspaceApi(Responder resp) {
		this.resp = resp;
	}

	public void rename(String srcPath, String dstPath) throws IOException {
		final RenameWorkspacePathRes res = new WorkspaceHandler()
				.handleRenameWorkspacePath(new RenameWorkspacePathReq(srcPath, dstPath));
		if (res.ok) {
			resp.respondOK();
		} else {
			resp.respondBadRequest();
		}
		final File workspaceRootFile = WorkspaceHandler.getWorkspaceRoot(false);
		if (workspaceRootFile == null) {
			resp.respondBadRequest();
			return;
		}
	}

	// This class is easier to test if we don't use the actual Socket class,
	// hence 'SocketLike'
	public interface SocketLike {
		InputStream getInputStream() throws IOException;
	}

	public void putContents(String path, int contentLen, SocketLike body) throws IOException {
		final PutWorkspaceContentRes res = new WorkspaceHandler().handlePutWorkspaceContent(path,
				out -> drain(contentLen, body, out));
		if (res.ok) {
			resp.respondOK();
		} else {
			resp.respondBadRequest();
		}
	}

	public void putMetadata(String path, int contentLen, SocketLike body) throws IOException {
		final PutWorkspaceMetadataRes res = new WorkspaceHandler().handlePutWorkspaceMetadata(path,
				contentLen == 0 ? null : out -> drain(contentLen, body, out));
		if (res.ok) {
			resp.respondOK();
		} else {
			resp.respondBadRequest();
		}
	}

	public void unlink(String path) throws IOException {
		final UnlinkWorkspacePathRes res = new WorkspaceHandler()
				.handleUnlinkWorkspacePath(new UnlinkWorkspacePathReq(path));
		if (res.ok) {
			resp.respondOK();
		} else {
			resp.respondBadRequest();
		}
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
		getContents(null);
	}

	public void getContents(String path) throws IOException {
		final WorkspaceHandler handler = new WorkspaceHandler();
		final File file = path == null ? WorkspaceHandler.getWorkspaceRoot(false) : handler.relativePathToFile(path);
		if (file == null) {
			resp.respondBadRequest();
			return;
		}

		final JSONObject respObj;
		if (file.isDirectory()) {
			final ListWorkspaceDirectoryRes res = handler
					.handleListWorkspaceDirectory(new ListWorkspaceDirectoryReq(path));
			if (res.entries == null) {
				resp.respondBadRequest();
				return;
			}
			final JSONArray arr = new JSONArray(res.entries.stream().map(x -> new JSONObject() //
					.put("t", x.isFile() ? "f" : "d") // type (file or dir)
					.put("n", x.isFile() ? x.asFile().name : x.asDirectory()) // name
			).collect(Collectors.toList()));
			respObj = new JSONObject().put("files", arr);
		} else {
			final GetWorkspaceFileRes res = handler.handleGetWorkspaceFile(new GetWorkspaceFileReq(path));
			if (res.content == null) {
				resp.respondBadRequest();
				return;
			}

			respObj = new JSONObject() //
					.put("text", res.content);
			if (res.metadata != null) {
				respObj.put("metadata", res.metadata);
			}
		}
		resp.respondOK(respObj.toString().getBytes(StandardCharsets.UTF_8));
	}
}
